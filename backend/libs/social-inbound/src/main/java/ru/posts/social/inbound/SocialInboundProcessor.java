package ru.posts.social.inbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.posts.messaging.ingest.SocialInboundCommand;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.ChannelEventSubscriptionEntity;
import ru.posts.persistence.entity.TelegramBotEntity;
import ru.posts.persistence.repository.ChannelEventSubscriptionRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.TelegramBotRepository;
import ru.posts.social.inbound.album.TelegramAlbumAggregator;
import ru.posts.social.inbound.dedup.InboundEventStore;
import ru.posts.social.inbound.metrics.SocialInboundMetrics;
import ru.posts.social.inbound.normalize.MaxEventNormalizer;
import ru.posts.social.inbound.normalize.TelegramEventNormalizer;
import ru.posts.social.inbound.normalize.VkEventNormalizer;

/** Consumes {@link SocialInboundCommand}, normalizes, dedups, dispatches handlers. */
public class SocialInboundProcessor {

    private static final Logger log = LoggerFactory.getLogger(SocialInboundProcessor.class);

    private final ObjectMapper objectMapper;
    private final ChannelRepository channelRepository;
    private final ChannelEventSubscriptionRepository subscriptionRepository;
    private final TelegramBotRepository telegramBotRepository;
    private final InboundEventStore eventStore;
    private final SocialEventDispatcher dispatcher;
    private final SocialInboundMetrics metrics;
    private final TelegramAlbumAggregator albumAggregator;

    public SocialInboundProcessor(
            ObjectMapper objectMapper,
            ChannelRepository channelRepository,
            ChannelEventSubscriptionRepository subscriptionRepository,
            TelegramBotRepository telegramBotRepository,
            InboundEventStore eventStore,
            SocialEventDispatcher dispatcher,
            SocialInboundMetrics metrics,
            TelegramAlbumAggregator albumAggregator) {
        this.objectMapper = objectMapper;
        this.channelRepository = channelRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.telegramBotRepository = telegramBotRepository;
        this.eventStore = eventStore;
        this.dispatcher = dispatcher;
        this.metrics = metrics;
        this.albumAggregator = albumAggregator;
    }

    public void process(SocialInboundCommand command) {
        try {
            JsonNode body = objectMapper.readTree(command.payloadJson());
            Instant receivedAt = command.enqueuedAt() == null ? Instant.now() : command.enqueuedAt();
            metrics.recordRetryCounter(command.network(), command.retryCounter());
            switch (command.network()) {
                case "vk" -> processVk(command, body, receivedAt);
                case "tg" -> processTg(command, body, receivedAt);
                case "max" -> processMax(command, body, receivedAt);
                default -> log.warn("unknown_inbound_network network={}", command.network());
            }
            metrics.recordLag(command.enqueuedAt());
        } catch (Exception ex) {
            log.error(
                    "social_inbound_process_failed network={} resourceId={} err={}",
                    command.network(),
                    command.resourceId(),
                    ex.toString());
            throw new IllegalStateException("social inbound processing failed", ex);
        }
    }

    private void processVk(SocialInboundCommand command, JsonNode body, Instant receivedAt) {
        UUID channelId = UUID.fromString(command.resourceId());
        ChannelEntity channel = channelRepository
                .findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        List<String> consumers = consumersFor(channelId);
        SocialInboundEvent event =
                VkEventNormalizer.normalize(body, channelId, channel.getWorkspaceId(), consumers, receivedAt);
        accept(event);
        touchSubscription(channelId, receivedAt);
    }

    private void processTg(SocialInboundCommand command, JsonNode body, Instant receivedAt) {
        UUID botId = UUID.fromString(command.resourceId());
        TelegramBotEntity bot =
                telegramBotRepository.findById(botId).orElseThrow(() -> new IllegalArgumentException("Bot not found"));
        List<ChannelEntity> channels = resolveTgChannels(bot, body);
        if (channels.isEmpty()) {
            log.info(
                    "tg_update_no_channel botId={} updateId={}",
                    botId,
                    body.path("update_id").asText());
            return;
        }
        JsonNode message =
                body.has("channel_post") ? body.get("channel_post") : body.has("message") ? body.get("message") : null;
        String mediaGroupId = message != null && message.has("media_group_id")
                ? message.path("media_group_id").asText()
                : null;

        if (mediaGroupId != null && !mediaGroupId.isBlank()) {
            for (ChannelEntity channel : channels) {
                albumAggregator.buffer(mediaGroupId, channel.getId().toString(), botId.toString(), body);
            }
            // Albums are flushed by {@link #flushReadyAlbums} after the 3s window.
            return;
        }

        for (ChannelEntity channel : channels) {
            List<String> consumers = consumersFor(channel.getId());
            SocialInboundEvent event = TelegramEventNormalizer.normalize(
                    body, botId, channel.getId(), channel.getWorkspaceId(), consumers, receivedAt);
            accept(event);
            touchSubscription(channel.getId(), receivedAt);
        }
    }

    /** Flush Telegram albums whose aggregation window elapsed. */
    public void flushReadyAlbums() {
        Instant now = Instant.now();
        for (TelegramAlbumAggregator.ReadyAlbum album : albumAggregator.drainReady(now)) {
            UUID channelId = UUID.fromString(album.channelKey());
            ChannelEntity channel =
                    channelRepository.findByIdAndDeletedAtIsNull(channelId).orElse(null);
            if (channel == null || album.fragments().isEmpty() || album.botId() == null) {
                continue;
            }
            UUID botId = UUID.fromString(album.botId());
            List<JsonNode> fragments = uniqueUpdates(album.fragments());
            List<String> consumers = consumersFor(channelId);
            SocialInboundEvent event = TelegramEventNormalizer.mergeAlbum(
                    fragments, botId, channelId, channel.getWorkspaceId(), consumers, now);
            accept(event);
            touchSubscription(channelId, now);
        }
    }

    private void processMax(SocialInboundCommand command, JsonNode body, Instant receivedAt) {
        UUID channelId = UUID.fromString(command.resourceId());
        ChannelEntity channel = channelRepository
                .findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        List<String> consumers = consumersFor(channelId);
        SocialInboundEvent event =
                MaxEventNormalizer.normalize(body, channelId, channel.getWorkspaceId(), consumers, receivedAt);
        accept(event);
        touchSubscription(channelId, receivedAt);
    }

    private void accept(SocialInboundEvent event) {
        Optional<UUID> inserted = eventStore.insertIfNew(event);
        if (inserted.isEmpty()) {
            metrics.recordDuplicate(event.network());
            metrics.recordResult(event.network(), event.kind().name(), "duplicate");
            log.debug(
                    "social_inbound_duplicate network={} channelId={} dedupKey={}",
                    event.network(),
                    event.channelId(),
                    event.dedupKey());
            return;
        }
        dispatcher.dispatch(event);
        eventStore.markProcessed(inserted.get(), event.receivedAt());
    }

    private List<String> consumersFor(UUID channelId) {
        return subscriptionRepository
                .findByChannelId(channelId)
                .map(ChannelEventSubscriptionEntity::getConsumers)
                .orElse(List.of("suggest"));
    }

    private void touchSubscription(UUID channelId, Instant receivedAt) {
        subscriptionRepository.findByChannelId(channelId).ifPresent(sub -> {
            sub.setLastEventAt(receivedAt);
            sub.setUpdatedAt(Instant.now());
            if ("degraded".equals(sub.getStatus()) || "failed".equals(sub.getStatus())) {
                sub.setStatus("active");
                sub.setLastError(null);
            }
            subscriptionRepository.save(sub);
        });
    }

    private List<ChannelEntity> resolveTgChannels(TelegramBotEntity bot, JsonNode body) {
        if ("custom".equals(bot.getKind()) && bot.getChannelId() != null) {
            return channelRepository
                    .findByIdAndDeletedAtIsNull(bot.getChannelId())
                    .map(List::of)
                    .orElse(List.of());
        }
        JsonNode message = body.has("channel_post")
                ? body.get("channel_post")
                : body.has("edited_channel_post")
                        ? body.get("edited_channel_post")
                        : body.has("message") ? body.get("message") : body.get("edited_message");
        if (message == null || message.isNull()) {
            return channelRepository.findByBotIdAndDeletedAtIsNull(bot.getId());
        }
        String chatId = message.path("chat").path("id").asText(null);
        if (chatId == null) {
            return channelRepository.findByBotIdAndDeletedAtIsNull(bot.getId());
        }
        List<ChannelEntity> byExternal = channelRepository.findByNetworkAndExternalIdAndDeletedAtIsNull("tg", chatId);
        if (!byExternal.isEmpty()) {
            return byExternal;
        }
        // username fallback
        String username = message.path("chat").path("username").asText(null);
        if (username != null) {
            return channelRepository.findByNetworkAndExternalIdAndDeletedAtIsNull("tg", "@" + username);
        }
        return List.of();
    }

    private static List<JsonNode> uniqueUpdates(List<JsonNode> album) {
        List<JsonNode> out = new ArrayList<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        for (JsonNode u : album) {
            long id = u.path("update_id").asLong(u.hashCode());
            if (seen.add(id)) {
                out.add(u);
            }
        }
        return out;
    }
}
