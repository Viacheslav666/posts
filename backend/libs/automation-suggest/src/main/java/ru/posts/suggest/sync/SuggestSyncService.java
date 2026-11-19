package ru.posts.suggest.sync;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.SuggestItemEntity;
import ru.posts.persistence.entity.SuggestSettingsEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.SuggestItemRepository;
import ru.posts.persistence.repository.SuggestSettingsRepository;
import ru.posts.suggest.SuggestProcessingService;
import ru.posts.suggest.metrics.SuggestMetrics;
import ru.posts.suggest.vk.SuggestVkClient;
import ru.posts.suggest.vk.SuggestVkClient.SuggestWallPost;

/** Reconciles suggest_items with VK wall.get filter=suggests (BE-34). */
@Profile("!openapi")
@Service
public class SuggestSyncService {

    private static final Logger log = LoggerFactory.getLogger(SuggestSyncService.class);

    private static final Set<String> OPEN_STATUSES =
            Set.of("new", "pending_moderation", "approved", "scheduled", "publishing");

    private final SuggestSettingsRepository settingsRepository;
    private final SuggestItemRepository itemRepository;
    private final ChannelRepository channelRepository;
    private final SuggestVkClient vkClient;
    private final SuggestProcessingService processingService;
    private final SuggestMetrics metrics;
    private final SuggestTokenResolver tokenResolver;

    public SuggestSyncService(
            SuggestSettingsRepository settingsRepository,
            SuggestItemRepository itemRepository,
            ChannelRepository channelRepository,
            SuggestVkClient vkClient,
            SuggestProcessingService processingService,
            SuggestMetrics metrics,
            SuggestTokenResolver tokenResolver) {
        this.settingsRepository = settingsRepository;
        this.itemRepository = itemRepository;
        this.channelRepository = channelRepository;
        this.vkClient = vkClient;
        this.processingService = processingService;
        this.metrics = metrics;
        this.tokenResolver = tokenResolver;
    }

    @Transactional
    public int syncDue(int limit) {
        Instant now = Instant.now();
        List<SuggestSettingsEntity> due =
                settingsRepository.findDueForSync(now, PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
        int synced = 0;
        for (SuggestSettingsEntity settings : due) {
            try {
                syncChannel(settings.getId(), 100);
                synced++;
            } catch (RuntimeException ex) {
                log.warn("suggest sync failed settingsId={}: {}", settings.getId(), ex.getMessage());
                scheduleNext(settings, now, false);
            }
        }
        return synced;
    }

    @Transactional
    public SuggestSyncResult syncChannel(UUID settingsId) {
        return syncChannel(settingsId, 100);
    }

    @Transactional
    public SuggestSyncResult syncChannel(UUID settingsId, int count) {
        SuggestSettingsEntity settings = settingsRepository
                .findById(settingsId)
                .orElseThrow(() -> new IllegalArgumentException("Suggest settings not found"));
        ChannelEntity channel = channelRepository
                .findById(settings.getChannelId())
                .orElseThrow(() -> new IllegalArgumentException("Channel not found"));
        String token = tokenResolver.resolveUserToken(channel);
        Instant now = Instant.now();
        if (token == null || token.isBlank()) {
            scheduleNext(settings, now, false);
            return new SuggestSyncResult(0, 0, 0, "missing_token");
        }

        List<SuggestWallPost> suggests = vkClient.getSuggests(channel.getExternalId(), token, count);
        Set<String> remoteIds = new HashSet<>();
        int imported = 0;
        for (SuggestWallPost post : suggests) {
            if (post.id() == null) {
                continue;
            }
            remoteIds.add(post.id());
            Optional<SuggestItemEntity> existing =
                    itemRepository.findByChannelIdAndVkPostId(channel.getId(), post.id());
            if (existing.isEmpty()) {
                processingService.processSuggested(
                        channel.getId(),
                        post.id(),
                        post.fromId(),
                        post.text(),
                        post.attachments(),
                        post.raw(),
                        null,
                        null,
                        settings.getCreatedBy());
                imported++;
            }
        }

        int removed = 0;
        int published = 0;
        List<SuggestItemEntity> open = itemRepository.findByChannelIdAndStatusIn(channel.getId(), OPEN_STATUSES);
        for (SuggestItemEntity item : open) {
            if (remoteIds.contains(item.getVkPostId())) {
                continue;
            }
            SuggestWallPost byId = null;
            try {
                byId = vkClient.getById(channel.getExternalId(), item.getVkPostId(), token);
            } catch (RuntimeException ex) {
                log.debug("wall.getById failed for {}: {}", item.getVkPostId(), ex.getMessage());
            }
            if (byId != null && isPublishedOnWall(byId)) {
                item.setStatus("published");
                item.setPublishedRemotePostId(byId.id());
                item.setUpdatedAt(now);
                itemRepository.save(item);
                metrics.recordStatus("published");
                published++;
            } else {
                item.setStatus("removed_externally");
                item.setUpdatedAt(now);
                itemRepository.save(item);
                metrics.recordStatus("removed_externally");
                removed++;
            }
        }

        scheduleNext(settings, now, true);
        return new SuggestSyncResult(imported, removed, published, "ok");
    }

    private boolean isPublishedOnWall(SuggestWallPost post) {
        String type = post.postType() == null ? "" : post.postType().toLowerCase(Locale.ROOT);
        return !"suggest".equals(type);
    }

    private void scheduleNext(SuggestSettingsEntity settings, Instant now, boolean success) {
        settings.setLastSyncAt(now);
        int minutes = success ? nextIntervalMinutes(settings) : 30;
        settings.setNextSyncAt(now.plus(minutes, ChronoUnit.MINUTES));
        settings.setUpdatedAt(now);
        settingsRepository.save(settings);
    }

    private int nextIntervalMinutes(SuggestSettingsEntity settings) {
        // Adaptive 10..60 based on open moderation queue size.
        long pending = itemRepository
                .findBySettingsIdAndStatusOrderByCreatedAtAsc(settings.getId(), "pending_moderation")
                .size();
        if (pending > 50) {
            return 10;
        }
        if (pending > 10) {
            return 20;
        }
        return 60;
    }

    public record SuggestSyncResult(int imported, int removedExternally, int markedPublished, String status) {}

    /** Resolves a user access token for wall.get(filter=suggests). */
    @FunctionalInterface
    public interface SuggestTokenResolver {
        String resolveUserToken(ChannelEntity channel);

        /** Fallback that returns a placeholder when no vault is wired (tests / openapi). */
        static SuggestTokenResolver noop() {
            return channel -> {
                if (channel.getSocialAccountId() == null) {
                    return null;
                }
                return "test-token";
            };
        }
    }
}
