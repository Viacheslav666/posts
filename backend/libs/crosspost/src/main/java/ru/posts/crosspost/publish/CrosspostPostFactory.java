package ru.posts.crosspost.publish;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.common.uuid.UuidV7;
import ru.posts.crosspost.config.CrosspostConfigSupport;
import ru.posts.crosspost.loop.LoopGuard;
import ru.posts.crosspost.model.CrosspostCandidate;
import ru.posts.crosspost.transform.CrosspostTransformer;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.CrosspostItemEntity;
import ru.posts.persistence.entity.CrosspostRuleEntity;
import ru.posts.persistence.entity.CrosspostRuleTargetEntity;
import ru.posts.persistence.entity.OutboxEntity;
import ru.posts.persistence.entity.PostEntity;
import ru.posts.persistence.entity.PostVariantEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.repository.OutboxRepository;
import ru.posts.persistence.repository.PostRepository;
import ru.posts.persistence.repository.PostVariantRepository;
import ru.posts.persistence.repository.PublicationRepository;

/**
 * Creates posts/variants/publications for crosspost items through the BE-10 pipeline. Idempotency
 * key: {@code xp:{ruleId}:{sourceChannelId}:{sourceRemoteId}:{targetChannelId}}.
 */
@Profile("!openapi")
@Component
public class CrosspostPostFactory {

    public record TargetPublish(
            UUID targetChannelId, UUID publicationId, String status, String reason, Map<String, Object> preview) {}

    public record PublishResult(UUID postId, List<TargetPublish> targets, boolean idempotent) {}

    private final PostRepository postRepository;
    private final PostVariantRepository postVariantRepository;
    private final PublicationRepository publicationRepository;
    private final OutboxRepository outboxRepository;

    public CrosspostPostFactory(
            PostRepository postRepository,
            PostVariantRepository postVariantRepository,
            PublicationRepository publicationRepository,
            OutboxRepository outboxRepository) {
        this.postRepository = postRepository;
        this.postVariantRepository = postVariantRepository;
        this.publicationRepository = publicationRepository;
        this.outboxRepository = outboxRepository;
    }

    public static String idempotencyKey(
            UUID ruleId, UUID sourceChannelId, String sourceRemoteId, UUID targetChannelId) {
        return "xp:" + ruleId + ":" + sourceChannelId + ":" + sourceRemoteId + ":" + targetChannelId;
    }

    @Transactional
    public PublishResult publish(
            CrosspostRuleEntity rule,
            CrosspostItemEntity item,
            CrosspostCandidate candidate,
            List<CrosspostRuleTargetEntity> targets,
            Map<UUID, ChannelEntity> channelsById,
            Map<UUID, LoopGuard.Decision> perTargetDecision,
            Instant runAtBase,
            boolean dryRun) {
        if (item.getPostId() != null && !dryRun) {
            UUID[] existing = item.getPublicationIds();
            List<TargetPublish> pubs = new ArrayList<>();
            if (existing != null) {
                for (UUID id : existing) {
                    pubs.add(new TargetPublish(null, id, "queued", null, Map.of()));
                }
            }
            return new PublishResult(item.getPostId(), pubs, true);
        }

        Instant now = Instant.now();
        int delayMin = CrosspostConfigSupport.delayMinutes(rule.getConfig());
        Instant scheduledAt = runAtBase.plusSeconds(delayMin * 60L);
        ensurePublicationPartition(scheduledAt);

        String sourcePermalink = sourcePermalink(candidate, channelsById.get(item.getSourceChannelId()));
        Map<String, Object> sourceRef = LoopGuard.buildSourceRef(
                rule.getId(),
                item.getId(),
                item.getSourceNetwork(),
                item.getSourceChannelId(),
                item.getSourceRemoteId(),
                Map.of());

        List<TargetPublish> results = new ArrayList<>();
        List<AcceptedTarget> accepted = new ArrayList<>();
        for (CrosspostRuleTargetEntity target : targets) {
            ChannelEntity channel = channelsById.get(target.getTargetChannelId());
            if (channel == null) {
                results.add(new TargetPublish(
                        target.getTargetChannelId(), null, "failed", "target_channel_missing", Map.of()));
                continue;
            }
            LoopGuard.Decision decision = perTargetDecision.get(target.getTargetChannelId());
            if (decision != null && decision.block()) {
                results.add(new TargetPublish(
                        target.getTargetChannelId(),
                        null,
                        decision.status(),
                        decision.reason(),
                        Map.of(
                                "layer",
                                decision.layer() == null ? "" : decision.layer().name())));
                continue;
            }
            CrosspostTransformer.TransformResult transformed = CrosspostTransformer.transform(
                    candidate, rule.getConfig(), target.getOptions(), channel.getNetwork(), sourcePermalink);
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("network", channel.getNetwork());
            preview.put("text", transformed.text());
            preview.put("overflowText", transformed.overflowText());
            preview.put("attachments", transformed.attachments());
            preview.put("options", transformed.options());
            preview.put("notes", transformed.notes());
            preview.put(
                    "idempotencyKey",
                    idempotencyKey(rule.getId(), item.getSourceChannelId(), item.getSourceRemoteId(), channel.getId()));
            if (dryRun) {
                results.add(new TargetPublish(channel.getId(), null, "preview", null, preview));
                continue;
            }
            accepted.add(new AcceptedTarget(channel, target, transformed, preview));
        }

        if (dryRun || accepted.isEmpty()) {
            return new PublishResult(null, results, false);
        }

        PostEntity post = new PostEntity();
        post.setId(UuidV7.generate());
        post.setWorkspaceId(rule.getWorkspaceId());
        post.setProjectId(rule.getProjectId());
        post.setAuthorId(rule.getCreatedBy());
        String titleText = candidate.text() == null ? "" : candidate.text();
        post.setTitle(truncate(titleText, 80));
        Map<String, Object> baseContent = new LinkedHashMap<>();
        baseContent.put("text", titleText);
        baseContent.put("mediaIds", List.of());
        post.setBaseContent(baseContent);
        post.setStatus("scheduled");
        post.setScheduledAt(scheduledAt);
        post.setContentKind("post");
        post.setSourceType("crosspost");
        post.setSourceRef(sourceRef);
        post.setModerationStatus("approved");
        post.setTimezone("Europe/Moscow");
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        postRepository.save(post);

        List<UUID> publicationIds = new ArrayList<>();
        for (AcceptedTarget at : accepted) {
            ChannelEntity channel = at.channel();
            CrosspostTransformer.TransformResult transformed = at.transformed();
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("text", transformed.text());
            content.put("mediaIds", List.of());
            if (transformed.overflowText() != null
                    && !transformed.overflowText().isBlank()) {
                content.put("followUpText", transformed.overflowText());
            }

            PostVariantEntity variant = new PostVariantEntity();
            variant.setId(UuidV7.generate());
            variant.setPostId(post.getId());
            variant.setChannelId(channel.getId());
            variant.setNetwork(channel.getNetwork());
            variant.setContent(content);
            variant.setOptions(transformed.options());
            variant.setCreatedAt(now);
            variant.setUpdatedAt(now);
            postVariantRepository.save(variant);

            String key =
                    idempotencyKey(rule.getId(), item.getSourceChannelId(), item.getSourceRemoteId(), channel.getId());
            PublicationEntity pub = new PublicationEntity();
            pub.setId(UuidV7.generate());
            pub.setWorkspaceId(rule.getWorkspaceId());
            pub.setPostId(post.getId());
            pub.setVariantId(variant.getId());
            pub.setChannelId(channel.getId());
            pub.setNetwork(channel.getNetwork());
            pub.setRunAt(scheduledAt);
            pub.setIdempotencyKey(key);
            pub.setState("pending");
            pub.setCreatedAt(now);
            publicationRepository.save(pub);
            publicationIds.add(pub.getId());
            results.add(new TargetPublish(channel.getId(), pub.getId(), "queued", null, at.preview()));
        }

        OutboxEntity outbox = new OutboxEntity();
        outbox.setAggregate("post");
        outbox.setAggregateId(post.getId());
        outbox.setEventType("post.scheduled");
        outbox.setPayload(Map.of(
                "workspaceId", rule.getWorkspaceId().toString(),
                "postId", post.getId().toString(),
                "sourceType", "crosspost",
                "itemId", item.getId().toString(),
                "status", post.getStatus()));
        outbox.setCreatedAt(now);
        outboxRepository.save(outbox);

        item.setPostId(post.getId());
        item.setPublicationIds(publicationIds.toArray(UUID[]::new));
        return new PublishResult(post.getId(), results, false);
    }

    private static String sourcePermalink(CrosspostCandidate candidate, ChannelEntity source) {
        if (source == null || candidate == null) {
            return null;
        }
        String ext = source.getExternalId();
        String remote = candidate.remoteId();
        return switch (candidate.network() == null ? "" : candidate.network()) {
            case "vk" -> "https://vk.com/wall-" + ext + "_" + remote;
            case "tg" -> source.getUsername() != null && !source.getUsername().isBlank()
                    ? "https://t.me/" + source.getUsername() + "/" + remote
                    : null;
            case "max" -> null;
            default -> null;
        };
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
            return "Crosspost";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private record AcceptedTarget(
            ChannelEntity channel,
            CrosspostRuleTargetEntity target,
            CrosspostTransformer.TransformResult transformed,
            Map<String, Object> preview) {}
}
