package ru.posts.publisher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.posts.persistence.jdbc.OutboxJdbc;
import ru.posts.persistence.jdbc.RemotePostRegistryJdbc;
import ru.posts.persistence.jdbc.SocialRemoteActionClaim;
import ru.posts.persistence.jdbc.SocialRemoteActionLifecycleJdbc;
import ru.posts.publisher.channel.ChannelAccessResolver;
import ru.posts.publisher.config.PublisherProperties;
import ru.posts.publisher.metrics.PublicationMetrics;
import ru.posts.ratelimit.RateLimitDecision;
import ru.posts.ratelimit.SocialRateLimiter;
import ru.posts.social.api.AccessTokenKind;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.ModerationBatchItem;
import ru.posts.social.api.ModerationBatchItemResult;
import ru.posts.social.api.ModerationFeatureFlags;
import ru.posts.social.api.ModerationPayload;
import ru.posts.social.api.ModerationResult;
import ru.posts.social.api.ModerationTarget;
import ru.posts.social.api.Network;
import ru.posts.social.api.SocialApiException;
import ru.posts.social.api.SocialModerationPort;

/** Processes social_remote_actions (BE-33 moderation / suggest). */
@Service
public class RemoteActionProcessor {

    private static final Logger log = LoggerFactory.getLogger(RemoteActionProcessor.class);
    private static final int VK_BATCH_DEPTH_THRESHOLD = 10;
    private static final int VK_BATCH_MAX = 25;

    private final SocialRemoteActionLifecycleJdbc lifecycleJdbc;
    private final RemotePostRegistryJdbc remotePostRegistryJdbc;
    private final OutboxJdbc outboxJdbc;
    private final ObjectProvider<SocialModerationPort> moderationPortProvider;
    private final SocialRateLimiter rateLimiter;
    private final TransactionTemplate transactionTemplate;
    private final PublisherProperties properties;
    private final PublicationMetrics metrics;
    private final ObjectMapper objectMapper;
    private final ChannelAccessResolver channelAccessResolver;

    public RemoteActionProcessor(
            SocialRemoteActionLifecycleJdbc lifecycleJdbc,
            RemotePostRegistryJdbc remotePostRegistryJdbc,
            OutboxJdbc outboxJdbc,
            ObjectProvider<SocialModerationPort> moderationPortProvider,
            SocialRateLimiter rateLimiter,
            TransactionTemplate transactionTemplate,
            PublisherProperties properties,
            PublicationMetrics metrics,
            ObjectMapper objectMapper,
            ChannelAccessResolver channelAccessResolver) {
        this.lifecycleJdbc = lifecycleJdbc;
        this.remotePostRegistryJdbc = remotePostRegistryJdbc;
        this.outboxJdbc = outboxJdbc;
        this.moderationPortProvider = moderationPortProvider;
        this.rateLimiter = rateLimiter;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.channelAccessResolver = channelAccessResolver;
    }

    public boolean process(UUID actionId) {
        Optional<SocialRemoteActionClaim> claimed = transactionTemplate.execute(
                status -> lifecycleJdbc.claimQueued(actionId, properties.podName(), properties.claimLockTtl()));
        if (claimed == null || claimed.isEmpty()) {
            return true;
        }
        SocialRemoteActionClaim claim = claimed.get();
        if (claim.network() != null && !claim.network().equals(properties.network())) {
            transactionTemplate.executeWithoutResult(status -> lifecycleJdbc.markFailedRetryable(
                    claim.id(), claim.runAt(), Instant.now().plusSeconds(5), "wrong_network", "network mismatch"));
            return true;
        }

        SocialModerationPort port = moderationPortProvider.getIfAvailable();
        if (port == null) {
            transactionTemplate.executeWithoutResult(status -> lifecycleJdbc.markFailedPermanent(
                    claim.id(), claim.runAt(), "moderation_port_missing", "SocialModerationPort bean not available"));
            metrics.recordRemoteAction(claim.network(), claim.kind(), "permanent");
            return true;
        }

        RateLimitDecision decision = rateLimiter.tryAcquire(
                claim.network(),
                claim.channelId() == null ? null : claim.channelId().toString(),
                claim.externalChannelId());
        if (!decision.allowed()) {
            Instant next = Instant.now().plus(decision.waitTime());
            transactionTemplate.executeWithoutResult(status ->
                    lifecycleJdbc.markFailedRetryable(claim.id(), claim.runAt(), next, "rate_limited", "waiting"));
            metrics.recordRemoteAction(claim.network(), claim.kind(), "rate_limited");
            return true;
        }

        ModerationTarget target = parseTarget(claim.targetJson());
        ModerationPayload payload = parsePayload(claim.payloadJson());
        AccessTokenKind tokenKind = ChannelAccessResolver.tokenKindFor(claim.network(), claim.kind(), target);
        ChannelContext ctx = channelAccessResolver.context(
                claim.workspaceId(),
                claim.channelId(),
                claim.externalChannelId(),
                Network.fromCode(claim.network()),
                tokenKind);

        if (!featureEnabled(port, claim.kind(), target)) {
            transactionTemplate.executeWithoutResult(status -> lifecycleJdbc.markFailedPermanent(
                    claim.id(), claim.runAt(), "permission", "moderation capability disabled for " + claim.kind()));
            metrics.recordRemoteAction(claim.network(), claim.kind(), "permission");
            return true;
        }

        if ("vk".equals(claim.network())
                && lifecycleJdbc.countPendingOrQueuedForChannel(claim.channelId()) > VK_BATCH_DEPTH_THRESHOLD) {
            return processVkBatch(port, claim, ctx, target, payload);
        }

        try {
            ModerationResult result = executeOne(port, ctx, claim.kind(), target, payload);
            return finishSuccess(claim, result);
        } catch (Exception ex) {
            return handleFailure(port, claim, ctx, target, ex);
        }
    }

    private boolean processVkBatch(
            SocialModerationPort port,
            SocialRemoteActionClaim primary,
            ChannelContext ctx,
            ModerationTarget primaryTarget,
            ModerationPayload primaryPayload) {
        List<SocialRemoteActionClaim> batch = new ArrayList<>();
        batch.add(primary);
        List<UUID> siblingIds =
                lifecycleJdbc.findQueuedIdsForChannel(primary.channelId(), primary.id(), VK_BATCH_MAX - 1);
        for (UUID siblingId : siblingIds) {
            Optional<SocialRemoteActionClaim> sibling = transactionTemplate.execute(
                    status -> lifecycleJdbc.claimQueued(siblingId, properties.podName(), properties.claimLockTtl()));
            if (sibling != null && sibling.isPresent()) {
                batch.add(sibling.get());
            }
        }

        List<ModerationBatchItem> items = new ArrayList<>(batch.size());
        Map<UUID, SocialRemoteActionClaim> byId = new LinkedHashMap<>();
        for (SocialRemoteActionClaim c : batch) {
            byId.put(c.id(), c);
            ModerationTarget t = c.id().equals(primary.id()) ? primaryTarget : parseTarget(c.targetJson());
            ModerationPayload p = c.id().equals(primary.id()) ? primaryPayload : parsePayload(c.payloadJson());
            if (!featureEnabled(port, c.kind(), t)) {
                transactionTemplate.executeWithoutResult(status -> lifecycleJdbc.markFailedPermanent(
                        c.id(), c.runAt(), "permission", "moderation capability disabled for " + c.kind()));
                metrics.recordRemoteAction(c.network(), c.kind(), "permission");
                byId.remove(c.id());
                continue;
            }
            items.add(new ModerationBatchItem(c.kind(), c.id(), t, p, c.idempotencyKey()));
        }
        if (items.isEmpty()) {
            return true;
        }

        List<ModerationBatchItemResult> results;
        try {
            results = port.executeBatch(ctx, items);
        } catch (Exception ex) {
            // Fall back to marking primary via normal failure path; siblings retry via reaper/AMQP.
            return handleFailure(port, primary, ctx, primaryTarget, ex);
        }

        for (ModerationBatchItemResult itemResult : results) {
            SocialRemoteActionClaim c = byId.get(itemResult.actionId());
            if (c == null) {
                continue;
            }
            if (itemResult.success()) {
                finishSuccess(c, itemResult.result());
            } else {
                ErrorClass errorClass =
                        itemResult.errorClass() == null ? ErrorClass.RETRYABLE : itemResult.errorClass();
                handleClassifiedFailure(
                        port,
                        c,
                        ctx,
                        parseTarget(c.targetJson()),
                        errorClass,
                        itemResult.errorMessage() == null ? "batch item failed" : itemResult.errorMessage(),
                        null);
            }
        }
        return true;
    }

    private ModerationResult executeOne(
            SocialModerationPort port,
            ChannelContext ctx,
            String kind,
            ModerationTarget target,
            ModerationPayload payload) {
        ModerationPayload safePayload = payload == null ? ModerationPayload.empty() : payload;
        return switch (normalizeKind(kind)) {
            case "publish_suggested" -> port.publishSuggested(ctx, target, safePayload);
            case "delete_post" -> port.deletePost(ctx, target);
            case "delete_comment" -> port.deleteComment(ctx, target);
            case "restore_comment" -> port.restoreComment(ctx, target);
            case "ban_user" -> port.banUser(ctx, target, safePayload);
            case "unban_user" -> port.unbanUser(ctx, target);
            case "reply_comment" -> port.replyToComment(ctx, target, safePayload);
            case "notify_author" -> port.notifyAuthor(ctx, target, safePayload);
            default -> throw new SocialApiException(ErrorClass.PERMANENT, "unknown moderation kind: " + kind);
        };
    }

    private boolean finishSuccess(SocialRemoteActionClaim claim, ModerationResult result) {
        ModerationResult safe = result == null ? new ModerationResult(null) : result;
        // alreadyGone on delete* → treat as success
        String resultJson = toResultJson(safe);
        transactionTemplate.executeWithoutResult(status -> {
            lifecycleJdbc.markSucceeded(claim.id(), claim.runAt(), resultJson);
            if ("publish_suggested".equals(normalizeKind(claim.kind()))
                    && safe.remoteId() != null
                    && !safe.remoteId().isBlank()) {
                remotePostRegistryJdbc.upsert(
                        claim.workspaceId(),
                        claim.network(),
                        claim.channelId(),
                        safe.remoteId(),
                        "suggest",
                        null,
                        null,
                        null,
                        (short) 0,
                        null);
            }
            outboxJdbc.append(
                    "remote_action",
                    claim.id(),
                    "remote_action.succeeded",
                    "{\"network\":\""
                            + claim.network()
                            + "\",\"kind\":\""
                            + claim.kind()
                            + "\",\"workspaceId\":\""
                            + claim.workspaceId()
                            + "\",\"remoteId\":\""
                            + (safe.remoteId() == null ? "" : safe.remoteId())
                            + "\"}");
        });
        metrics.recordRemoteAction(claim.network(), claim.kind(), "success");
        return true;
    }

    private boolean handleFailure(
            SocialModerationPort port,
            SocialRemoteActionClaim claim,
            ChannelContext ctx,
            ModerationTarget target,
            Exception ex) {
        Throwable root = unwrap(ex);
        ErrorClass errorClass = port.classifyError(root);
        if (root instanceof SocialApiException social) {
            errorClass = social.errorClass();
        }
        String message = root.getMessage() == null ? root.toString() : root.getMessage();
        Duration retryAfter =
                root instanceof SocialApiException social ? social.retryAfter().orElse(null) : null;
        return handleClassifiedFailure(port, claim, ctx, target, errorClass, message, retryAfter);
    }

    private boolean handleClassifiedFailure(
            SocialModerationPort port,
            SocialRemoteActionClaim claim,
            ChannelContext ctx,
            ModerationTarget target,
            ErrorClass errorClass,
            String message,
            Duration retryAfter) {
        if (errorClass == ErrorClass.UNKNOWN_OUTCOME && "publish_suggested".equals(normalizeKind(claim.kind()))) {
            transactionTemplate.executeWithoutResult(
                    status -> lifecycleJdbc.markUnknownOutcome(claim.id(), claim.runAt(), "unknown_outcome", message));
            try {
                Optional<ModerationResult> recovered =
                        port.reconcilePublishSuggested(ctx, target, claim.idempotencyKey());
                if (recovered.isPresent()) {
                    // Transition unknown_outcome → succeeded via markSucceeded.
                    return finishSuccess(claim, recovered.get());
                }
            } catch (Exception reconcileEx) {
                log.warn("reconcilePublishSuggested failed for {}: {}", claim.id(), reconcileEx.toString());
            }
            Instant next = PublicationBackoff.nextAttemptAt(claim.attempts(), Instant.now(), retryAfter);
            transactionTemplate.executeWithoutResult(status ->
                    lifecycleJdbc.markFailedRetryable(claim.id(), claim.runAt(), next, "unknown_outcome", message));
            metrics.recordRemoteAction(claim.network(), claim.kind(), "unknown_outcome");
            return true;
        }

        if (errorClass == ErrorClass.PERMISSION
                || errorClass == ErrorClass.AUTH
                || errorClass == ErrorClass.PERMANENT) {
            String code = errorClass == ErrorClass.PERMISSION
                    ? "permission"
                    : errorClass.name().toLowerCase(Locale.ROOT);
            transactionTemplate.executeWithoutResult(
                    status -> lifecycleJdbc.markFailedPermanent(claim.id(), claim.runAt(), code, message));
            metrics.recordRemoteAction(claim.network(), claim.kind(), code);
            return true;
        }

        if (errorClass == ErrorClass.RATE_LIMITED) {
            Duration wait = retryAfter == null ? Duration.ofSeconds(5) : retryAfter;
            if (wait.compareTo(Duration.ofSeconds(30)) >= 0) {
                rateLimiter.penalize(
                        claim.network(),
                        claim.channelId() == null ? null : claim.channelId().toString(),
                        claim.externalChannelId(),
                        wait.compareTo(Duration.ofHours(1)) > 0 ? Duration.ofHours(1) : wait);
            }
            Instant next = Instant.now().plus(wait);
            transactionTemplate.executeWithoutResult(status ->
                    lifecycleJdbc.markFailedRetryable(claim.id(), claim.runAt(), next, "rate_limited", message));
            metrics.recordRemoteAction(claim.network(), claim.kind(), "rate_limited");
            return true;
        }

        if (PublicationBackoff.exhausted(
                claim.attempts(),
                claim.runAt(),
                Instant.now(),
                properties.maxAttempts(),
                properties.maxAgeAfterRunAt())) {
            transactionTemplate.executeWithoutResult(status ->
                    lifecycleJdbc.markFailedPermanent(claim.id(), claim.runAt(), "attempts_exhausted", message));
            metrics.recordRemoteAction(claim.network(), claim.kind(), "exhausted");
            return true;
        }

        Instant next = PublicationBackoff.nextAttemptAt(claim.attempts(), Instant.now(), retryAfter);
        transactionTemplate.executeWithoutResult(
                status -> lifecycleJdbc.markFailedRetryable(claim.id(), claim.runAt(), next, "retryable", message));
        metrics.recordRemoteAction(claim.network(), claim.kind(), "retryable");
        return true;
    }

    private boolean featureEnabled(SocialModerationPort port, String kind, ModerationTarget target) {
        String feature = featureKey(kind, target);
        if (feature == null) {
            return false;
        }
        Boolean enabled = port.moderationCapabilities().features().get(feature);
        return Boolean.TRUE.equals(enabled);
    }

    private static String featureKey(String kind, ModerationTarget target) {
        return switch (normalizeKind(kind)) {
            case "publish_suggested" -> ModerationFeatureFlags.PUBLISH_SUGGESTED;
            case "delete_post" -> ModerationFeatureFlags.DELETE_POST;
            case "delete_comment" -> deleteCommentFeature(target);
            case "restore_comment" -> ModerationFeatureFlags.RESTORE_COMMENT;
            case "ban_user", "unban_user" -> ModerationFeatureFlags.BAN_USER;
            case "reply_comment" -> ModerationFeatureFlags.REPLY_TO_COMMENT;
            case "notify_author" -> ModerationFeatureFlags.NOTIFY_AUTHOR;
            default -> null;
        };
    }

    private static String deleteCommentFeature(ModerationTarget target) {
        String type = target == null || target.commentType() == null
                ? ""
                : target.commentType().trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "photo" -> ModerationFeatureFlags.DELETE_COMMENT_PHOTO;
            case "video" -> ModerationFeatureFlags.DELETE_COMMENT_VIDEO;
            case "board" -> ModerationFeatureFlags.DELETE_COMMENT_BOARD;
            case "market" -> ModerationFeatureFlags.DELETE_COMMENT_MARKET;
            case "discussion" -> ModerationFeatureFlags.DELETE_COMMENT_DISCUSSION;
            case "channel_comment" -> ModerationFeatureFlags.DELETE_COMMENT_CHANNEL_COMMENT;
            default -> ModerationFeatureFlags.DELETE_COMMENT_WALL;
        };
    }

    private ModerationTarget parseTarget(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new ModerationTarget(null, null, null, null, null);
            }
            return objectMapper.readValue(json, ModerationTarget.class);
        } catch (Exception ex) {
            log.warn("Failed to parse moderation target JSON: {}", ex.toString());
            return new ModerationTarget(null, null, null, null, null);
        }
    }

    private ModerationPayload parsePayload(String json) {
        try {
            if (json == null || json.isBlank()) {
                return ModerationPayload.empty();
            }
            return objectMapper.readValue(json, ModerationPayload.class);
        } catch (Exception ex) {
            log.warn("Failed to parse moderation payload JSON: {}", ex.toString());
            return ModerationPayload.empty();
        }
    }

    private String toResultJson(ModerationResult result) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("remoteId", result.remoteId());
            map.put("alreadyGone", result.alreadyGone());
            map.put("raw", result.raw());
            return objectMapper.writeValueAsString(map);
        } catch (Exception ex) {
            return "{\"remoteId\":\"" + (result.remoteId() == null ? "" : result.remoteId()) + "\"}";
        }
    }

    private static String normalizeKind(String kind) {
        return kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                        || current instanceof java.util.concurrent.ExecutionException
                        || (current instanceof RuntimeException && current.getClass() == RuntimeException.class))) {
            current = current.getCause();
        }
        return current;
    }
}
