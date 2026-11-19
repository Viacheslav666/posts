package ru.posts.suggest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.common.uuid.UuidV7;
import ru.posts.content.hash.ContentFingerprint;
import ru.posts.content.hash.SimHasher;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.SuggestItemEntity;
import ru.posts.persistence.entity.SuggestSettingsEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.SuggestItemRepository;
import ru.posts.persistence.repository.SuggestSettingsRepository;
import ru.posts.persistence.service.RemoteActionService;
import ru.posts.suggest.config.SuggestConfigSupport;
import ru.posts.suggest.filter.SuggestAction;
import ru.posts.suggest.filter.SuggestDecision;
import ru.posts.suggest.filter.SuggestFilterContext;
import ru.posts.suggest.filter.SuggestFilterEngine;
import ru.posts.suggest.filter.SuggestItemView;
import ru.posts.suggest.filter.SuggestReason;
import ru.posts.suggest.metrics.SuggestMetrics;
import ru.posts.suggest.schedule.SuggestSlotScheduler;

/** Main suggest processing flow for inbound events and sync imports (BE-34). */
@Service
public class SuggestProcessingService {

    private static final Logger log = LoggerFactory.getLogger(SuggestProcessingService.class);

    private static final Set<String> PUBLISH_STATUSES = Set.of("approved", "scheduled", "publishing", "published");

    private final SuggestSettingsRepository settingsRepository;
    private final SuggestItemRepository itemRepository;
    private final ChannelRepository channelRepository;
    private final SuggestFilterEngine filterEngine;
    private final SuggestSlotScheduler slotScheduler;
    private final RemoteActionService remoteActionService;
    private final SuggestMetrics metrics;
    private final ObjectMapper objectMapper;

    public SuggestProcessingService(
            SuggestSettingsRepository settingsRepository,
            SuggestItemRepository itemRepository,
            ChannelRepository channelRepository,
            SuggestFilterEngine filterEngine,
            SuggestSlotScheduler slotScheduler,
            RemoteActionService remoteActionService,
            SuggestMetrics metrics,
            ObjectMapper objectMapper) {
        this.settingsRepository = settingsRepository;
        this.itemRepository = itemRepository;
        this.channelRepository = channelRepository;
        this.filterEngine = filterEngine;
        this.slotScheduler = slotScheduler;
        this.remoteActionService = remoteActionService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    /**
     * Process a suggested post for a channel. Idempotent on (channelId, vkPostId).
     *
     * @return existing or newly created item; empty if settings inactive/missing
     */
    @Transactional
    public Optional<SuggestItemEntity> processSuggested(
            UUID channelId,
            String vkPostId,
            String authorVkId,
            String text,
            List<Map<String, Object>> attachments,
            Map<String, Object> raw,
            Boolean authorMessagesAllowed,
            Boolean authorClosedProfile,
            UUID createdBy) {
        Instant started = Instant.now();
        Optional<SuggestSettingsEntity> settingsOpt = settingsRepository.findByChannelId(channelId);
        if (settingsOpt.isEmpty() || !"active".equals(settingsOpt.get().getStatus())) {
            return Optional.empty();
        }
        SuggestSettingsEntity settings = settingsOpt.get();

        Optional<SuggestItemEntity> existing = itemRepository.findByChannelIdAndVkPostId(channelId, vkPostId);
        if (existing.isPresent()) {
            return existing;
        }

        Map<String, Object> config = SuggestConfigSupport.withDefaults(settings.getConfig());
        long simhash = SimHasher.fingerprint(text);
        String fingerprint = ContentFingerprint.fingerprint(text, List.of());

        SuggestFilterContext ctx =
                buildContext(settings, authorVkId, authorMessagesAllowed, authorClosedProfile, config, started);
        SuggestItemView view = new SuggestItemView(
                vkPostId,
                authorVkId,
                text,
                attachments,
                simhash,
                false,
                text != null && text.toLowerCase(java.util.Locale.ROOT).contains("erid"));
        SuggestDecision decision = filterEngine.evaluate(view, config, ctx);

        SuggestItemEntity item = new SuggestItemEntity();
        item.setId(UuidV7.generate());
        item.setWorkspaceId(settings.getWorkspaceId());
        item.setChannelId(channelId);
        item.setSettingsId(settings.getId());
        item.setVkPostId(vkPostId);
        item.setAuthorVkId(authorVkId);
        item.setText(text);
        item.setAttachments(attachments == null ? List.of() : attachments);
        item.setContentFingerprint(fingerprint);
        item.setSimhash(simhash);
        item.setRaw(raw);
        item.setReasons(toReasonCodes(decision.reasons()));
        item.setCreatedAt(started);
        item.setUpdatedAt(started);

        try {
            applyDecision(settings, item, decision, config, started, createdBy, null, null);
            itemRepository.save(item);
        } catch (DataIntegrityViolationException ex) {
            return itemRepository.findByChannelIdAndVkPostId(channelId, vkPostId);
        }

        metrics.recordStatus(item.getStatus());
        metrics.recordDecision(
                Duration.between(started, Instant.now()),
                decision.action() == SuggestAction.PUBLISH,
                decision.action() == SuggestAction.MODERATE);
        return Optional.of(item);
    }

    /** Manual approve of a pending item (optionally with edited message / schedule). */
    @Transactional
    public SuggestItemEntity approve(
            SuggestItemEntity item,
            SuggestSettingsEntity settings,
            UUID actorId,
            String messageOverride,
            List<Map<String, Object>> attachmentsOverride,
            Instant scheduledFor) {
        Map<String, Object> config = SuggestConfigSupport.withDefaults(settings.getConfig());
        if (messageOverride != null) {
            item.setText(messageOverride);
            item.setSimhash(SimHasher.fingerprint(messageOverride));
            item.setContentFingerprint(ContentFingerprint.fingerprint(messageOverride, List.of()));
        }
        if (attachmentsOverride != null) {
            item.setAttachments(attachmentsOverride);
        }
        Instant now = Instant.now();
        item.setDecidedBy(actorId);
        item.setDecidedAt(now);
        item.setReasons(new String[] {"manual_approve"});
        SuggestDecision decision = SuggestDecision.publish();
        applyDecision(settings, item, decision, config, now, actorId, scheduledFor, messageOverride);
        item.setUpdatedAt(now);
        SuggestItemEntity saved = itemRepository.save(item);
        metrics.recordStatus(saved.getStatus());
        return saved;
    }

    /** Manual reject. */
    @Transactional
    public SuggestItemEntity reject(
            SuggestItemEntity item, SuggestSettingsEntity settings, UUID actorId, boolean deleteRemote) {
        Map<String, Object> config = SuggestConfigSupport.withDefaults(settings.getConfig());
        Instant now = Instant.now();
        item.setDecidedBy(actorId);
        item.setDecidedAt(now);
        item.setStatus("rejected");
        item.setReasons(new String[] {"manual_reject"});
        item.setUpdatedAt(now);
        if (deleteRemote) {
            enqueueDelete(settings, item, now, actorId, now);
        } else if (SuggestConfigSupport.REJECT_DELETE.equals(SuggestConfigSupport.rejectPolicy(config))) {
            enqueueDelete(settings, item, now, actorId, now);
        } else if (SuggestConfigSupport.REJECT_DELETE_AFTER.equals(SuggestConfigSupport.rejectPolicy(config))) {
            Instant runAt = now.plus(SuggestConfigSupport.deleteAfterHours(config), ChronoUnit.HOURS);
            enqueueDelete(settings, item, now, actorId, runAt);
        }
        if (SuggestConfigSupport.notifyAuthor(config)) {
            enqueueNotify(settings, item, "Ваша предложенная запись отклонена", now, actorId);
        }
        SuggestItemEntity saved = itemRepository.save(item);
        metrics.recordStatus(saved.getStatus());
        return saved;
    }

    private void applyDecision(
            SuggestSettingsEntity settings,
            SuggestItemEntity item,
            SuggestDecision decision,
            Map<String, Object> config,
            Instant now,
            UUID actorId,
            Instant scheduledOverride,
            String messageOverride) {
        switch (decision.action()) {
            case PUBLISH -> enqueuePublish(settings, item, config, now, actorId, scheduledOverride, messageOverride);
            case MODERATE -> {
                item.setStatus("pending_moderation");
            }
            case REJECT -> {
                item.setStatus("rejected");
                applyRejectPolicy(settings, item, config, now, actorId, true);
            }
            case FILTER_OUT -> {
                item.setStatus("filtered_out");
                applyRejectPolicy(settings, item, config, now, actorId, false);
            }
        }
    }

    private void applyRejectPolicy(
            SuggestSettingsEntity settings,
            SuggestItemEntity item,
            Map<String, Object> config,
            Instant now,
            UUID actorId,
            boolean rejected) {
        String policy = SuggestConfigSupport.rejectPolicy(config);
        if (SuggestConfigSupport.REJECT_DELETE.equals(policy)) {
            enqueueDelete(settings, item, now, actorId, now);
        } else if (SuggestConfigSupport.REJECT_DELETE_AFTER.equals(policy)) {
            Instant runAt = now.plus(SuggestConfigSupport.deleteAfterHours(config), ChronoUnit.HOURS);
            enqueueDelete(settings, item, now, actorId, runAt);
        }
        if (SuggestConfigSupport.notifyAuthor(config)) {
            String msg = rejected
                    ? "Ваша предложенная запись отклонена: " + String.join(", ", item.getReasons())
                    : "Ваша предложенная запись не прошла фильтры: " + String.join(", ", item.getReasons());
            enqueueNotify(settings, item, msg, now, actorId);
        }
    }

    private void enqueuePublish(
            SuggestSettingsEntity settings,
            SuggestItemEntity item,
            Map<String, Object> config,
            Instant now,
            UUID actorId,
            Instant scheduledOverride,
            String messageOverride) {
        SuggestSettingsEntity locked =
                settingsRepository.findByIdForUpdate(settings.getId()).orElse(settings);
        Instant lastPublish = itemRepository
                .findFirstByChannelIdAndStatusInOrderByDecidedAtDesc(item.getChannelId(), PUBLISH_STATUSES)
                .map(i -> i.getScheduledFor() != null ? i.getScheduledFor() : i.getDecidedAt())
                .orElse(null);
        boolean lastAds = false; // populated by sync/context; schedule still respects pause_after_ads when true
        Instant slot = scheduledOverride != null
                ? scheduledOverride
                : slotScheduler.nextSlot(config, now, lastPublish, lastAds);

        ChannelEntity channel = channelRepository.findById(item.getChannelId()).orElse(null);
        String ownerId = channel == null ? null : channel.getExternalId();
        String message = buildMessage(item, config, messageOverride);
        boolean signed = SuggestConfigSupport.signed(config);

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("remotePostId", item.getVkPostId());
        if (ownerId != null) {
            target.put("ownerId", ownerId);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signed", signed);
        if (message != null) {
            payload.put("message", message);
            payload.put("text", message);
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("guid", "suggest:" + item.getChannelId() + ":" + item.getVkPostId());
        String attachmentCsv = formatVkAttachments(item.getAttachments());
        if (attachmentCsv != null) {
            extra.put("attachments", attachmentCsv);
        }
        payload.put("extra", extra);

        String idempotencyKey = "suggest:" + item.getChannelId() + ":" + item.getVkPostId();
        UUID actionId = remoteActionService.enqueue(
                "publish_suggested",
                item.getWorkspaceId(),
                item.getChannelId(),
                toJson(target),
                toJson(payload),
                idempotencyKey,
                slot,
                "suggest",
                toJson(Map.of("suggestItemId", item.getId().toString())),
                actorId);

        item.setRemoteActionId(actionId);
        item.setScheduledFor(slot);
        item.setStatus(slot.isAfter(now.plusSeconds(2)) ? "scheduled" : "publishing");
        item.setDecidedAt(now);
        if (actorId != null) {
            item.setDecidedBy(actorId);
        }

        Map<String, Object> firstComment = SuggestConfigSupport.firstComment(config);
        if (SuggestConfigSupport.bool(firstComment.get("enabled"), false)) {
            String commentText = SuggestConfigSupport.string(firstComment.get("text"), "");
            if (!commentText.isBlank()) {
                Map<String, Object> commentTarget = new LinkedHashMap<>();
                commentTarget.put("remotePostId", item.getVkPostId());
                if (ownerId != null) {
                    commentTarget.put("ownerId", ownerId);
                }
                Map<String, Object> commentPayload = Map.of("messageText", commentText, "text", commentText);
                remoteActionService.enqueue(
                        "reply_comment",
                        item.getWorkspaceId(),
                        item.getChannelId(),
                        toJson(commentTarget),
                        toJson(commentPayload),
                        "suggest-comment:" + item.getChannelId() + ":" + item.getVkPostId(),
                        slot.plusSeconds(5),
                        "suggest",
                        toJson(Map.of("suggestItemId", item.getId().toString())),
                        actorId);
            }
        }
        // touch locked settings updated_at so version bumps under lock
        locked.setUpdatedAt(now);
        settingsRepository.save(locked);
    }

    private void enqueueDelete(
            SuggestSettingsEntity settings, SuggestItemEntity item, Instant now, UUID actorId, Instant runAt) {
        ChannelEntity channel = channelRepository.findById(item.getChannelId()).orElse(null);
        String ownerId = channel == null ? null : channel.getExternalId();
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("remotePostId", item.getVkPostId());
        if (ownerId != null) {
            target.put("ownerId", ownerId);
        }
        remoteActionService.enqueue(
                "delete_post",
                item.getWorkspaceId(),
                item.getChannelId(),
                toJson(target),
                "{}",
                "suggest-delete:" + item.getChannelId() + ":" + item.getVkPostId(),
                runAt,
                "suggest",
                toJson(Map.of(
                        "suggestItemId", item.getId().toString(),
                        "settingsId", settings.getId().toString())),
                actorId);
        if (!runAt.isAfter(now.plusSeconds(1))) {
            item.setStatus("deleted_remote");
        }
    }

    private void enqueueNotify(
            SuggestSettingsEntity settings, SuggestItemEntity item, String message, Instant now, UUID actorId) {
        if (item.getAuthorVkId() == null || item.getAuthorVkId().isBlank()) {
            return;
        }
        ChannelEntity channel = channelRepository.findById(item.getChannelId()).orElse(null);
        String ownerId = channel == null ? null : channel.getExternalId();
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("remoteUserId", item.getAuthorVkId());
        if (ownerId != null) {
            target.put("ownerId", ownerId);
        }
        Map<String, Object> payload = Map.of("messageText", message, "text", message);
        try {
            remoteActionService.enqueue(
                    "notify_author",
                    item.getWorkspaceId(),
                    item.getChannelId(),
                    toJson(target),
                    toJson(payload),
                    "suggest-notify:" + item.getChannelId() + ":" + item.getVkPostId(),
                    now,
                    "suggest",
                    toJson(Map.of(
                            "suggestItemId", item.getId().toString(),
                            "settingsId", settings.getId().toString())),
                    actorId);
        } catch (RuntimeException ex) {
            log.debug("notify_author enqueue skipped: {}", ex.getMessage());
        }
    }

    private SuggestFilterContext buildContext(
            SuggestSettingsEntity settings,
            String authorVkId,
            Boolean messagesAllowed,
            Boolean closedProfile,
            Map<String, Object> config,
            Instant now) {
        Instant hourAgo = now.minus(1, ChronoUnit.HOURS);
        Instant dayAgo = now.minus(1, ChronoUnit.DAYS);
        Instant weekAgo = now.minus(7, ChronoUnit.DAYS);
        SuggestFilterContext.AuthorCounts authorCounts = SuggestFilterContext.AuthorCounts.ZERO;
        if (authorVkId != null && !authorVkId.isBlank()) {
            authorCounts = new SuggestFilterContext.AuthorCounts(
                    itemRepository.countByChannelIdAndAuthorVkIdAndStatusInAndCreatedAtAfter(
                            settings.getChannelId(), authorVkId, PUBLISH_STATUSES, hourAgo),
                    itemRepository.countByChannelIdAndAuthorVkIdAndStatusInAndCreatedAtAfter(
                            settings.getChannelId(), authorVkId, PUBLISH_STATUSES, dayAgo),
                    itemRepository.countByChannelIdAndAuthorVkIdAndStatusInAndCreatedAtAfter(
                            settings.getChannelId(), authorVkId, PUBLISH_STATUSES, weekAgo));
        }
        SuggestFilterContext.AuthorCounts globalCounts = new SuggestFilterContext.AuthorCounts(
                itemRepository.countByChannelIdAndStatusInAndCreatedAtAfter(
                        settings.getChannelId(), PUBLISH_STATUSES, hourAgo),
                itemRepository.countByChannelIdAndStatusInAndCreatedAtAfter(
                        settings.getChannelId(), PUBLISH_STATUSES, dayAgo),
                itemRepository.countByChannelIdAndStatusInAndCreatedAtAfter(
                        settings.getChannelId(), PUBLISH_STATUSES, weekAgo));

        Instant since = now.minus(SuggestConfigSupport.lookbackDays(config), ChronoUnit.DAYS);
        List<Long> recent = itemRepository
                .findRecentWithSimhashByChannel(
                        settings.getChannelId(),
                        Set.of("filtered_out", "rejected", "deleted_remote", "removed_externally", "failed"),
                        since,
                        PageRequest.of(0, 50))
                .stream()
                .map(SuggestItemEntity::getSimhash)
                .filter(s -> s != null)
                .toList();

        return new SuggestFilterContext(
                settings.getMode(), messagesAllowed, closedProfile, recent, authorCounts, globalCounts, false, now);
    }

    private static String formatVkAttachments(List<Map<String, Object>> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> att : attachments) {
            if (att == null) {
                continue;
            }
            Object type = att.get("type");
            Object ownerId = att.get("owner_id");
            Object id = att.get("id");
            if (type == null || ownerId == null || id == null) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(type).append(ownerId).append('_').append(id);
            Object accessKey = att.get("access_key");
            if (accessKey != null && !accessKey.toString().isBlank()) {
                sb.append('_').append(accessKey);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String buildMessage(SuggestItemEntity item, Map<String, Object> config, String override) {
        String base = override != null ? override : (item.getText() == null ? "" : item.getText());
        Map<String, Object> enrichment = SuggestConfigSupport.enrichment(config);
        String append = SuggestConfigSupport.string(enrichment.get("append_text"), "");
        List<String> hashtags = SuggestConfigSupport.stringList(enrichment.get("hashtags"));
        StringBuilder sb = new StringBuilder(base == null ? "" : base);
        if (!append.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(append);
        }
        if (!hashtags.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(String.join(" ", hashtags));
        }
        return sb.toString();
    }

    private static String[] toReasonCodes(List<SuggestReason> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return new String[0];
        }
        List<String> codes = new ArrayList<>();
        for (SuggestReason r : reasons) {
            codes.add(r.code());
        }
        return codes.toArray(String[]::new);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize JSON", ex);
        }
    }
}
