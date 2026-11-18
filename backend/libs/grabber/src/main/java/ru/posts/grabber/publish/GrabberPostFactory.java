package ru.posts.grabber.publish;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.common.uuid.UuidV7;
import ru.posts.grabber.config.GrabberConfigSupport;
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

    private final PostRepository postRepository;
    private final PostVariantRepository postVariantRepository;
    private final PublicationRepository publicationRepository;
    private final ChannelRepository channelRepository;
    private final OutboxRepository outboxRepository;

    public GrabberPostFactory(
            PostRepository postRepository,
            PostVariantRepository postVariantRepository,
            PublicationRepository publicationRepository,
            ChannelRepository channelRepository,
            OutboxRepository outboxRepository) {
        this.postRepository = postRepository;
        this.postVariantRepository = postVariantRepository;
        this.publicationRepository = publicationRepository;
        this.channelRepository = channelRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public PublishResult publishItem(
            AutomationRuleEntity rule, GrabberItemEntity item, UUID actorId, String overrideText, Instant runAt) {
        if (item.getPostId() != null) {
            UUID[] existing = item.getPublicationIds();
            List<UUID> pubs = existing == null ? List.of() : List.of(existing);
            return new PublishResult(item.getPostId(), pubs, true);
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
            item.setStatus("failed_validation");
            item.setSkipReason("text_too_long");
            item.setUpdatedAt(Instant.now());
            return new PublishResult(null, List.of(), false);
        }

        Instant now = Instant.now();
        Instant scheduledAt = runAt == null ? now : runAt;
        ensurePublicationPartition(scheduledAt);

        PostEntity post = new PostEntity();
        post.setId(UuidV7.generate());
        post.setWorkspaceId(rule.getWorkspaceId());
        post.setProjectId(rule.getProjectId());
        post.setAuthorId(actorId == null ? rule.getCreatedBy() : actorId);
        post.setTitle(truncate(finalText, 80));
        Map<String, Object> baseContent = new LinkedHashMap<>();
        baseContent.put("text", finalText == null ? "" : finalText);
        baseContent.put("mediaIds", List.of());
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
        Object mode = GrabberConfigSupport.transform(rule.getConfig()).get("attachments_mode");
        if (mode != null && !mode.toString().isBlank()) {
            options.put("primaryAttachmentsMode", mode.toString());
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
        variant.setContent(Map.of("text", finalText == null ? "" : finalText, "mediaIds", List.of()));
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
        return new PublishResult(post.getId(), List.of(pub.getId()), false);
    }

    private static void ensurePublicationPartition(Instant runAt) {
        YearMonth ym = YearMonth.from(runAt.atZone(ZoneOffset.UTC));
        YearMonth min = YearMonth.of(2026, 10);
        YearMonth max = YearMonth.of(2027, 9);
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
