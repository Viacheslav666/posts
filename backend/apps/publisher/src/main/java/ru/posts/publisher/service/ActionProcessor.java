package ru.posts.publisher.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.posts.ord.reporting.OrdCreativeLifecycleService;
import ru.posts.persistence.jdbc.ScheduledActionClaim;
import ru.posts.persistence.jdbc.ScheduledActionLifecycleJdbc;
import ru.posts.publisher.channel.ChannelAccessResolver;
import ru.posts.publisher.config.PublisherProperties;
import ru.posts.publisher.metrics.PublicationMetrics;
import ru.posts.ratelimit.RateLimitDecision;
import ru.posts.ratelimit.SocialRateLimiter;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.Network;
import ru.posts.social.api.SocialAdapter;
import ru.posts.social.api.SocialApiException;

/** Processes scheduled_actions (delete / unpin / first_comment / pin / delete_story). */
@Service
public class ActionProcessor {

    private static final Logger log = LoggerFactory.getLogger(ActionProcessor.class);

    private final ScheduledActionLifecycleJdbc lifecycleJdbc;
    private final SocialAdapter socialAdapter;
    private final SocialRateLimiter rateLimiter;
    private final TransactionTemplate transactionTemplate;
    private final PublisherProperties properties;
    private final PublicationMetrics metrics;
    private final ObjectMapper objectMapper;
    private final OrdCreativeLifecycleService creativeLifecycleService;
    private final ChannelAccessResolver channelAccessResolver;

    public ActionProcessor(
            ScheduledActionLifecycleJdbc lifecycleJdbc,
            SocialAdapter socialAdapter,
            SocialRateLimiter rateLimiter,
            TransactionTemplate transactionTemplate,
            PublisherProperties properties,
            PublicationMetrics metrics,
            ObjectMapper objectMapper,
            OrdCreativeLifecycleService creativeLifecycleService,
            ChannelAccessResolver channelAccessResolver) {
        this.lifecycleJdbc = lifecycleJdbc;
        this.socialAdapter = socialAdapter;
        this.rateLimiter = rateLimiter;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.creativeLifecycleService = creativeLifecycleService;
        this.channelAccessResolver = channelAccessResolver;
    }

    public boolean process(UUID actionId) {
        Optional<ScheduledActionClaim> claimed = transactionTemplate.execute(
                status -> lifecycleJdbc.claimQueued(actionId, properties.podName(), properties.claimLockTtl()));
        if (claimed == null || claimed.isEmpty()) {
            return true;
        }
        ScheduledActionClaim claim = claimed.get();
        if (claim.network() != null && !claim.network().equals(properties.network())) {
            // Wrong network queue routing — requeue via retryable so the right publisher can pick it up.
            transactionTemplate.executeWithoutResult(status -> lifecycleJdbc.markFailedRetryable(
                    claim.id(), claim.runAt(), Instant.now().plusSeconds(5), "wrong_network", "network mismatch"));
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
            metrics.recordAction(claim.network(), claim.kind(), "rate_limited");
            return true;
        }

        ChannelContext ctx = channelAccessResolver.context(
                claim.workspaceId(), claim.channelId(), claim.externalChannelId(), Network.fromCode(claim.network()));
        String remotePostId = remoteId(claim);
        try {
            execute(claim, ctx, remotePostId);
            transactionTemplate.executeWithoutResult(status -> {
                lifecycleJdbc.markSucceeded(claim.id(), claim.runAt());
                if ("delete".equals(claim.kind()) || "delete_story".equals(claim.kind())) {
                    if (claim.postId() != null) {
                        creativeLifecycleService.markDisplayEnded(claim.postId(), Instant.now());
                    }
                }
            });
            metrics.recordAction(claim.network(), claim.kind(), "success");
            return true;
        } catch (Exception ex) {
            return handleFailure(claim, ex);
        }
    }

    private void execute(ScheduledActionClaim claim, ChannelContext ctx, String remotePostId) {
        String kind = claim.kind();
        switch (kind) {
            case "delete" -> socialAdapter.delete(ctx, remotePostId);
            case "delete_story" -> socialAdapter.deleteStory(ctx, remotePostId);
            case "pin" -> socialAdapter.pin(ctx, remotePostId, true);
            case "unpin" -> socialAdapter.pin(ctx, remotePostId, false);
            case "first_comment" -> comment(ctx, remotePostId, claim);
            case "edit" -> log.info("edit action deferred to BE-15+/content edit path for {}", claim.id());
            default -> throw new SocialApiException(ErrorClass.PERMANENT, "unknown action kind: " + kind);
        }
    }

    private boolean handleFailure(ScheduledActionClaim claim, Exception ex) {
        Throwable root = unwrap(ex);
        ErrorClass errorClass = socialAdapter.classifyError(root);
        String message = root.getMessage() == null ? root.toString() : root.getMessage();
        if (errorClass == ErrorClass.AUTH || errorClass == ErrorClass.PERMANENT) {
            transactionTemplate.executeWithoutResult(status -> lifecycleJdbc.markFailedPermanent(
                    claim.id(), claim.runAt(), errorClass.name().toLowerCase(Locale.ROOT), message));
            metrics.recordAction(claim.network(), claim.kind(), "permanent");
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
            metrics.recordAction(claim.network(), claim.kind(), "exhausted");
            return true;
        }
        Duration retryAfter =
                root instanceof SocialApiException social ? social.retryAfter().orElse(null) : null;
        Instant next = PublicationBackoff.nextAttemptAt(claim.attempts(), Instant.now(), retryAfter);
        transactionTemplate.executeWithoutResult(
                status -> lifecycleJdbc.markFailedRetryable(claim.id(), claim.runAt(), next, "retryable", message));
        metrics.recordAction(claim.network(), claim.kind(), "retryable");
        return true;
    }

    private String remoteId(ScheduledActionClaim claim) {
        JsonNode payload = parse(claim.payloadJson());
        String fromPayload = payload.path("remotePostId").asText(null);
        if (fromPayload != null && !fromPayload.isBlank()) {
            return fromPayload;
        }
        return claim.remotePostId();
    }

    private void comment(ChannelContext ctx, String remotePostId, ScheduledActionClaim claim) {
        JsonNode payload = parse(claim.payloadJson());
        String text = payload.path("text").asText("");
        List<String> attachments = new ArrayList<>();
        JsonNode raw = payload.get("attachments");
        if (raw != null && raw.isArray()) {
            raw.forEach(item -> {
                if (item != null && item.isTextual() && !item.asText().isBlank()) {
                    attachments.add(item.asText());
                }
            });
        }
        if (attachments.isEmpty()) {
            socialAdapter.comment(ctx, remotePostId, text);
        } else {
            socialAdapter.comment(ctx, remotePostId, text, attachments);
        }
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
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
