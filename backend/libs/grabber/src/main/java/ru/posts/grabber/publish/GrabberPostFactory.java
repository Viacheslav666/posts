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
import ru.posts.grabber.ai.GrabberAiRewriteService;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.config.GrabberProperties;
import ru.posts.grabber.notify.GrabberSettingReporter;
import ru.posts.grabber.target.GrabberPublishSchedule;
import ru.posts.grabber.target.GrabberRepostObject;
import ru.posts.grabber.target.GrabberTargetMode;
import ru.posts.grabber.target.PostponedHorizonException;
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
    private final GrabberAiRewriteService aiRewriteService;
    private final GrabberSettingReporter settingReporter;
    private final GrabberProperties grabberProperties;

    public GrabberPostFactory(
            PostRepository postRepository,
            PostVariantRepository postVariantRepository,
            PublicationRepository publicationRepository,
            ChannelRepository channelRepository,
            OutboxRepository outboxRepository,
            GrabberAiRewriteService aiRewriteService,
            GrabberSettingReporter settingReporter,
            GrabberProperties grabberProperties) {
        this.postRepository = postRepository;
        this.postVariantRepository = postVariantRepository;
        this.publicationRepository = publicationRepository;
        this.channelRepository = channelRepository;
        this.outboxRepository = outboxRepository;
        this.aiRewriteService = aiRewriteService;
        this.settingReporter = settingReporter;
        this.grabberProperties = grabberProperties;
    }

    @Transactional
    public PublishResult publishItem(
            AutomationRuleEntity rule, GrabberItemEntity item, UUID actorId, String overrideText, Instant runAt) {
        return publishItem(rule, item, actorId, overrideText, runAt, 0);
    }

    @Transactional
    public PublishResult publishItem(
            AutomationRuleEntity rule,
            GrabberItemEntity item,
            UUID actorId,
            String overrideText,
            Instant runAt,
            int slotIndex) {
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
        String targetModeEarly = GrabberConfigSupport.targetMode(rule.getConfig());
        if (GrabberTargetMode.VK_POSTPONED_REPOST.wire().equals(targetModeEarly)
                && !grabberProperties.isPostponedRepostEnabled()) {
            item.setStatus("failed_validation");
            item.setSkipReason("postponed_repost_unavailable");
            item.setUpdatedAt(Instant.now());
            return new PublishResult(null, List.of(), false);
        }

        String sourceText = sourceText(item);
        String signerName = signerName(item);
        MacroExpander.Context macroCtx =
                MacroExpander.Context.of(item.getSourceName(), item.getSourceId(), item.getRemotePostId(), sourceText);
        UUID actor = actorId == null ? rule.getCreatedBy() : actorId;
        GrabberAiRewriteService.Attempt ai;
        String finalText;
        if (overrideText != null) {
            ai = GrabberAiRewriteService.Attempt.unchanged(overrideText);
            finalText = finish(rule, item, overrideText, signerName, actor);
        } else {
            ai = aiRewriteService.prepare(rule, item, sourceText, actor);
            String body = ai.applied() ? ai.text() : sourceText;
            finalText = finish(rule, item, transformOnce(rule, item, body, macroCtx), signerName, actor);
        }

        int maxLen = 4096;
        if (finalText.length() > maxLen) {
            item.setStatus("failed_validation");
            item.setSkipReason("text_too_long");
            item.setUpdatedAt(Instant.now());
            return new PublishResult(null, List.of(), false);
        }
        if (ai.applied() && !aiRewriteService.charge(rule, item, ai, actor)) {
            finalText = finish(rule, item, transformOnce(rule, item, sourceText, macroCtx), signerName, actor);
            if (finalText.length() > maxLen) {
                item.setStatus("failed_validation");
                item.setSkipReason("text_too_long");
                item.setUpdatedAt(Instant.now());
                return new PublishResult(null, List.of(), false);
            }
        }

        Instant now = Instant.now();
        GrabberPublishSchedule.Plan plan;
        try {
            plan = GrabberPublishSchedule.plan(rule.getConfig(), now, runAt, slotIndex);
        } catch (PostponedHorizonException ex) {
            item.setStatus("failed_validation");
            item.setSkipReason("postponed_horizon");
            item.setUpdatedAt(now);
            return new PublishResult(null, List.of(), false);
        }
        String targetMode = GrabberConfigSupport.targetMode(rule.getConfig());
        String repostObject = null;
        if (GrabberTargetMode.isRepost(targetMode)) {
            repostObject = GrabberRepostObject.from(item.getSourceId(), item.getRemotePostId(), item.getRawPayload());
            if (repostObject == null) {
                item.setStatus("failed_validation");
                item.setSkipReason("repost_object_missing");
                item.setUpdatedAt(now);
                return new PublishResult(null, List.of(), false);
            }
        }
        Instant scheduledAt = plan.pipelineRunAt();
        ensurePublicationPartition(scheduledAt);

        PostEntity post = new PostEntity();
        post.setId(UuidV7.generate());
        post.setWorkspaceId(rule.getWorkspaceId());
        post.setProjectId(rule.getProjectId());
        post.setAuthorId(actor);
        post.setTitle(truncate(finalText, 80));
        Map<String, Object> baseContent = new LinkedHashMap<>();
        baseContent.put("text", finalText == null ? "" : finalText);
        baseContent.put("mediaIds", List.of());
        if (repostObject != null) {
            Map<String, Object> repost = new LinkedHashMap<>();
            repost.put("object", repostObject);
            repost.put("comment", finalText == null ? "" : finalText);
            repost.put("network", "vk");
            baseContent.put("repost", repost);
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
        post.setTimezone(GrabberConfigSupport.scheduleTimezone(rule.getConfig()));
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        postRepository.save(post);

        GrabberPublishOptions.Built built = GrabberPublishOptions.build(
                rule.getConfig(),
                macroCtx,
                scheduledAt,
                grabberProperties.getActionButton().isEnabled());
        for (GrabberPublishOptions.Notice notice : built.notices()) {
            settingReporter.reportUserNotice(actor, rule, notice.setting(), notice.code(), notice.message());
        }
        Map<String, Object> options = new LinkedHashMap<>(built.options());
        options.put("grabberTargetMode", targetMode);
        if (repostObject != null) {
            Map<String, Object> repost = new LinkedHashMap<>();
            repost.put("object", repostObject);
            repost.put("message", finalText == null ? "" : finalText);
            options.put("repost", repost);
        }
        if (plan.vkPublishDateEpoch() != null) {
            options.put("vkPublishDate", plan.vkPublishDateEpoch());
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

    private static String transformOnce(
            AutomationRuleEntity rule, GrabberItemEntity item, String body, MacroExpander.Context macroCtx) {
        return ContentTransformer.transform(body, item.getAttachmentsJson(), rule.getConfig(), macroCtx)
                .text();
    }

    private String finish(
            AutomationRuleEntity rule, GrabberItemEntity item, String body, String signerName, UUID actor) {
        String signed = applyAuthorSignature(rule, item, body, signerName, actor);
        String text = ContentTransformer.withAttribution(
                signed, rule.getConfig(), item.getSourceName(), item.getSourcePermalink());
        return text == null ? "" : text;
    }

    private String applyAuthorSignature(
            AutomationRuleEntity rule, GrabberItemEntity item, String text, String signerName, UUID actor) {
        if (!GrabberConfigSupport.appendAuthorSignature(rule.getConfig())) {
            return text == null ? "" : text;
        }
        if (signed(item) && (signerName == null || signerName.isBlank())) {
            settingReporter.reportUserNotice(
                    actor,
                    rule,
                    "author_signature",
                    "signer_missing",
                    "У поста есть подпись автора, но имя не пришло из VK. Подпись не добавлена.");
            return text == null ? "" : text;
        }
        return ContentTransformer.appendAuthorSignature(text, signerName);
    }

    private static String sourceText(GrabberItemEntity item) {
        Map<String, Object> raw = item.getRawPayload();
        if (raw != null
                && raw.get("source_text") != null
                && !raw.get("source_text").toString().isBlank()) {
            return raw.get("source_text").toString();
        }
        return item.getContentText() == null ? "" : item.getContentText();
    }

    private static String signerName(GrabberItemEntity item) {
        Map<String, Object> raw = item.getRawPayload();
        if (raw == null || raw.get("signer_name") == null) {
            return "";
        }
        return raw.get("signer_name").toString().trim();
    }

    private static boolean signed(GrabberItemEntity item) {
        Map<String, Object> raw = item.getRawPayload();
        if (raw == null || raw.get("signed") == null) {
            return false;
        }
        Object value = raw.get("signed");
        if (value instanceof Boolean flag) {
            return flag;
        }
        return Boolean.parseBoolean(value.toString());
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
