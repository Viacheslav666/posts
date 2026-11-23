package ru.posts.grabber.publish;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.posts.common.uuid.UuidV7;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.media.GrabberMediaResolver;
import ru.posts.grabber.ssrf.UrlRedactor;
import ru.posts.grabber.transform.ContentTransformer;
import ru.posts.grabber.transform.MacroExpander;
import ru.posts.persistence.entity.AutomationRuleEntity;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.GrabberItemEntity;
import ru.posts.persistence.entity.OutboxEntity;
import ru.posts.persistence.entity.PostEntity;
import ru.posts.persistence.entity.PostVariantEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.GrabberItemRepository;
import ru.posts.persistence.repository.OutboxRepository;
import ru.posts.persistence.repository.PostRepository;
import ru.posts.persistence.repository.PostVariantRepository;
import ru.posts.persistence.repository.PublicationRepository;

/**
 * Creates posts/publications for grabber items through the same BE-10 pipeline path (pending
 * publications + outbox). Never publishes out-of-band.
 */
@Component
@Profile("!openapi")
public class GrabberPostFactory {

    private static final Logger log = LoggerFactory.getLogger(GrabberPostFactory.class);

    private final PostRepository postRepository;
    private final PostVariantRepository postVariantRepository;
    private final PublicationRepository publicationRepository;
    private final ChannelRepository channelRepository;
    private final GrabberItemRepository itemRepository;
    private final OutboxRepository outboxRepository;
    private final GrabberMediaResolver mediaResolver;
    private final TransactionTemplate tx;

    public GrabberPostFactory(
            PostRepository postRepository,
            PostVariantRepository postVariantRepository,
            PublicationRepository publicationRepository,
            ChannelRepository channelRepository,
            GrabberItemRepository itemRepository,
            OutboxRepository outboxRepository,
            GrabberMediaResolver mediaResolver,
            PlatformTransactionManager transactionManager) {
        this.postRepository = postRepository;
        this.postVariantRepository = postVariantRepository;
        this.publicationRepository = publicationRepository;
        this.channelRepository = channelRepository;
        this.itemRepository = itemRepository;
        this.outboxRepository = outboxRepository;
        this.mediaResolver = mediaResolver;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Downloads and re-uploads media outside any database transaction, then persists the post in a
     * short transaction that locks the item row.
     */
    public PublishResult publishItem(
            AutomationRuleEntity rule, GrabberItemEntity item, UUID actorId, String overrideText, Instant runAt) {
        if (item.getPostId() != null) {
            return alreadyPublished(item);
        }
        ChannelEntity channel = channelRepository
                .findByIdAndDeletedAtIsNull(rule.getTargetChannelId())
                .orElseThrow(() -> new IllegalStateException("Target channel not found"));
        if ("reauth_required".equals(channel.getStatus())) {
            throw new ChannelReauthRequiredException("Target channel requires reauth");
        }

        MacroExpander.Context macroCtx =
                MacroExpander.Context.of(item.getSourceName(), item.getSourceId(), item.getRemotePostId());
        String baseText = overrideText != null ? overrideText : item.getContentText();
        ContentTransformer.TransformResult transformed =
                ContentTransformer.transform(baseText, item.getAttachmentsJson(), rule.getConfig(), macroCtx);
        String finalText = ContentTransformer.withAttribution(
                transformed.text(), rule.getConfig(), item.getSourceName(), item.getSourcePermalink());

        // Network text limit (VK ~4096) — fail validation without wall.post
        int maxLen = 4096;
        if (finalText != null && finalText.length() > maxLen) {
            return tx.execute(status -> {
                GrabberItemEntity locked = lockItem(item);
                if (locked.getPostId() != null) {
                    return alreadyPublished(locked, item);
                }
                locked.setStatus("failed_validation");
                locked.setSkipReason("text_too_long");
                locked.setUpdatedAt(Instant.now());
                itemRepository.save(locked);
                copyPublishState(locked, item);
                return new PublishResult(null, List.of(), false);
            });
        }

        Instant scheduledAt = runAt == null ? Instant.now() : runAt;
        ensurePublicationPartition(scheduledAt);

        // Network I/O. Callers must not hold a transaction across this line.
        GrabberMediaResolver.Resolution media =
                mediaResolver.resolve(rule, channel, transformed.attachments(), transformed.text());
        if (media.failed()) {
            return tx.execute(status -> failLocked(rule, item, media));
        }
        List<String> stringAttachments = media.attachments();
        boolean carousel = media.carousel();

        return tx.execute(status -> persistPublished(
                rule,
                item,
                channel,
                actorId,
                overrideText,
                finalText,
                macroCtx,
                stringAttachments,
                carousel,
                scheduledAt));
    }

    private PublishResult persistPublished(
            AutomationRuleEntity rule,
            GrabberItemEntity passed,
            ChannelEntity channel,
            UUID actorId,
            String overrideText,
            String finalText,
            MacroExpander.Context macroCtx,
            List<String> stringAttachments,
            boolean carousel,
            Instant scheduledAt) {
        GrabberItemEntity item = lockItem(passed);
        if (item.getPostId() != null) {
            return alreadyPublished(item, passed);
        }
        if (overrideText != null) {
            item.setEditedText(overrideText);
            item.setContentText(overrideText);
        }
        Instant now = Instant.now();

        PostEntity post = new PostEntity();
        post.setId(UuidV7.generate());
        post.setWorkspaceId(rule.getWorkspaceId());
        post.setProjectId(rule.getProjectId());
        post.setAuthorId(actorId == null ? rule.getCreatedBy() : actorId);
        post.setTitle(truncate(finalText, 80));
        Map<String, Object> baseContent = new LinkedHashMap<>();
        baseContent.put("text", finalText == null ? "" : finalText);
        baseContent.put("mediaIds", List.of());
        if (!stringAttachments.isEmpty()) {
            baseContent.put("stringAttachments", stringAttachments);
        }
        post.setBaseContent(baseContent);
        post.setStatus("scheduled");
        post.setScheduledAt(scheduledAt);
        post.setContentKind("post");
        String sourceType = "rss".equals(rule.getType()) ? "rss" : "grabber";
        post.setSourceType(sourceType);
        Map<String, Object> sourceRef = new LinkedHashMap<>();
        sourceRef.put("ruleId", rule.getId().toString());
        sourceRef.put("itemId", item.getId().toString());
        sourceRef.put("sourceNetwork", item.getSourceNetwork());
        sourceRef.put("sourceId", item.getSourceId());
        sourceRef.put("remotePostId", item.getRemotePostId());
        sourceRef.put("permalink", item.getSourcePermalink());
        sourceRef.put("contentHash", item.getContentHash());
        post.setSourceRef(sourceRef);
        post.setModerationStatus("approved");
        post.setTimezone("Europe/Moscow");
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        postRepository.save(post);

        Map<String, Object> options = new LinkedHashMap<>();
        if (carousel) {
            options.put("primaryAttachmentsMode", "carousel");
        }
        // BE-23 stub: do NOT set "firstComment" (that would schedule VK comment via adapters).
        // Keep draft under grabberFirstComment* until BE-23 wires comment adapters.
        String firstComment = ContentTransformer.firstCommentDraft(rule.getConfig(), macroCtx);
        if (firstComment != null && !firstComment.isBlank()) {
            options.put("grabberFirstComment", firstComment);
            options.put("grabberFirstCommentPendingBe23", true);
            Map<String, Object> fc = GrabberConfigSupport.firstComment(rule.getConfig());
            Object atts = fc.get("attachments");
            if (atts != null) {
                options.put("grabberFirstCommentAttachments", atts);
            }
        }

        PostVariantEntity variant = new PostVariantEntity();
        variant.setId(UuidV7.generate());
        variant.setPostId(post.getId());
        variant.setChannelId(channel.getId());
        variant.setNetwork(channel.getNetwork());
        Map<String, Object> variantContent = new LinkedHashMap<>();
        variantContent.put("text", finalText == null ? "" : finalText);
        variantContent.put("mediaIds", List.of());
        if (!stringAttachments.isEmpty()) {
            variantContent.put("stringAttachments", stringAttachments);
        }
        variant.setContent(variantContent);
        variant.setOptions(options);
        variant.setCreatedAt(now);
        variant.setUpdatedAt(now);
        postVariantRepository.save(variant);

        PublicationEntity pub = new PublicationEntity();
        pub.setId(UuidV7.generate());
        pub.setWorkspaceId(rule.getWorkspaceId());
        pub.setPostId(post.getId());
        pub.setVariantId(variant.getId());
        pub.setChannelId(channel.getId());
        pub.setNetwork(channel.getNetwork());
        pub.setRunAt(scheduledAt);
        pub.setIdempotencyKey("grabber:" + item.getId() + ":" + scheduledAt.getEpochSecond());
        pub.setState("pending");
        pub.setCreatedAt(now);
        publicationRepository.save(pub);

        OutboxEntity outbox = new OutboxEntity();
        outbox.setAggregate("post");
        outbox.setAggregateId(post.getId());
        outbox.setEventType("post.scheduled");
        outbox.setPayload(Map.of(
                "workspaceId", rule.getWorkspaceId().toString(),
                "postId", post.getId().toString(),
                "sourceType", sourceType,
                "itemId", item.getId().toString(),
                "status", post.getStatus()));
        outbox.setCreatedAt(now);
        outboxRepository.save(outbox);

        item.setPostId(post.getId());
        item.setPublicationIds(new UUID[] {pub.getId()});
        item.setStatus("scheduled");
        item.setUpdatedAt(now);
        itemRepository.save(item);
        copyPublishState(item, passed);
        return new PublishResult(post.getId(), List.of(pub.getId()), false);
    }

    private PublishResult failLocked(
            AutomationRuleEntity rule, GrabberItemEntity passed, GrabberMediaResolver.Resolution media) {
        GrabberItemEntity item = lockItem(passed);
        if (item.getPostId() != null) {
            return alreadyPublished(item, passed);
        }
        failItem(rule, item, media);
        itemRepository.save(item);
        copyPublishState(item, passed);
        return new PublishResult(null, List.of(), false);
    }

    private GrabberItemEntity lockItem(GrabberItemEntity item) {
        return itemRepository
                .findByIdForUpdate(item.getId())
                .orElseThrow(() -> new IllegalStateException("Grabber item not found: " + item.getId()));
    }

    private static PublishResult alreadyPublished(GrabberItemEntity item) {
        UUID[] existing = item.getPublicationIds();
        List<UUID> pubs = existing == null ? List.of() : List.of(existing);
        return new PublishResult(item.getPostId(), pubs, true);
    }

    private static PublishResult alreadyPublished(GrabberItemEntity locked, GrabberItemEntity passed) {
        copyPublishState(locked, passed);
        return alreadyPublished(locked);
    }

    private static void copyPublishState(GrabberItemEntity from, GrabberItemEntity to) {
        if (from == to) {
            return;
        }
        to.setPostId(from.getPostId());
        to.setPublicationIds(from.getPublicationIds());
        to.setStatus(from.getStatus());
        to.setSkipReason(from.getSkipReason());
        to.setContentText(from.getContentText());
        to.setEditedText(from.getEditedText());
        to.setUpdatedAt(from.getUpdatedAt());
    }

    /**
     * Required media could not be prepared (e.g. "Перезалить видео" is on and the re-upload failed):
     * the post is not published; the item is marked failed and, per rule setting, users are notified.
     */
    private void failItem(AutomationRuleEntity rule, GrabberItemEntity item, GrabberMediaResolver.Resolution media) {
        Instant now = Instant.now();
        String reason = media.failureReason() + (media.failureCode() == null ? "" : ":" + media.failureCode());
        String safeMessage = UrlRedactor.stripQuery(media.failureMessage());
        log.warn(
                "Grabber item not published ruleId={} itemId={} source={} reason={}: {}",
                rule.getId(),
                item.getId(),
                item.getSourcePermalink(),
                reason,
                safeMessage);
        item.setStatus("publish_failed");
        item.setSkipReason(reason);
        item.setUpdatedAt(now);
        if (!GrabberConfigSupport.notifyOnErrors(rule.getConfig())) {
            return;
        }
        if (outboxRepository.existsByAggregateAndAggregateIdAndEventType(
                "grabber_item", item.getId(), "grabber.item_failed")) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceId", rule.getWorkspaceId().toString());
        payload.put("ruleId", rule.getId().toString());
        payload.put("ruleName", rule.getName() == null ? "" : rule.getName());
        payload.put("itemId", item.getId().toString());
        payload.put("sourcePermalink", item.getSourcePermalink() == null ? "" : item.getSourcePermalink());
        payload.put("reason", reason);
        String message = safeMessage == null ? "" : safeMessage;
        payload.put("errorMessage", message.substring(0, Math.min(message.length(), 500)));
        OutboxEntity outbox = new OutboxEntity();
        outbox.setAggregate("grabber_item");
        outbox.setAggregateId(item.getId());
        outbox.setEventType("grabber.item_failed");
        outbox.setPayload(payload);
        outbox.setCreatedAt(now);
        outboxRepository.save(outbox);
    }

    private static void ensurePublicationPartition(Instant runAt) {
        YearMonth ym = YearMonth.from(runAt.atZone(ZoneOffset.UTC));
        YearMonth min = YearMonth.of(2026, 10);
        YearMonth max = YearMonth.now(ZoneOffset.UTC).plusMonths(18);
        if (ym.isBefore(min) || ym.isAfter(max)) {
            throw new IllegalArgumentException("scheduledAt outside publication partitions (" + min + ".." + max + ")");
        }
    }

    private static String truncate(String text, int max) {
        if (text == null || text.isBlank()) {
            return "Grabber post";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    public record PublishResult(UUID postId, List<UUID> publicationIds, boolean alreadyPublished) {}

    public static class ChannelReauthRequiredException extends RuntimeException {
        public ChannelReauthRequiredException(String message) {
            super(message);
        }
    }
}
