package ru.posts.publisher.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.posts.content.hash.ContentFingerprint;
import ru.posts.persistence.jdbc.MediaAssetLoadJdbc;
import ru.posts.persistence.jdbc.OutboxJdbc;
import ru.posts.persistence.jdbc.PublicationClaim;
import ru.posts.persistence.jdbc.PublicationLifecycleJdbc;
import ru.posts.persistence.jdbc.RemotePostRegistryJdbc;
import ru.posts.publisher.channel.ChannelAccessResolver;
import ru.posts.publisher.config.PublisherProperties;
import ru.posts.publisher.extension.PrePublishHook;
import ru.posts.publisher.media.MediaPublishSupport;
import ru.posts.publisher.metrics.PublicationMetrics;
import ru.posts.ratelimit.RateLimitDecision;
import ru.posts.ratelimit.SocialRateLimiter;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.Network;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.PublishResult;
import ru.posts.social.api.SocialAdapter;
import ru.posts.social.api.SocialApiException;

@Service
public class PublicationProcessor {

    private static final Logger log = LoggerFactory.getLogger(PublicationProcessor.class);

    private final PublicationLifecycleJdbc lifecycleJdbc;
    private final OutboxJdbc outboxJdbc;
    private final RemotePostRegistryJdbc remotePostRegistryJdbc;
    private final MediaAssetLoadJdbc mediaAssetLoadJdbc;
    private final ScheduledActionsFactory scheduledActionsFactory;
    private final SocialAdapter socialAdapter;
    private final SocialRateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;
    private final Bulkhead bulkhead;
    private final ExecutorService executorService;
    private final TransactionTemplate transactionTemplate;
    private final PublisherProperties properties;
    private final PublicationMetrics metrics;
    private final List<PrePublishHook> prePublishHooks;
    private final MediaPublishSupport mediaPublishSupport;
    private final ChannelAccessResolver channelAccessResolver;
    private final ObjectMapper objectMapper;

    public PublicationProcessor(
            PublicationLifecycleJdbc lifecycleJdbc,
            OutboxJdbc outboxJdbc,
            RemotePostRegistryJdbc remotePostRegistryJdbc,
            MediaAssetLoadJdbc mediaAssetLoadJdbc,
            ScheduledActionsFactory scheduledActionsFactory,
            SocialAdapter socialAdapter,
            SocialRateLimiter rateLimiter,
            CircuitBreaker socialCircuitBreaker,
            TimeLimiter socialTimeLimiter,
            Bulkhead socialBulkhead,
            ExecutorService publisherVirtualExecutor,
            TransactionTemplate transactionTemplate,
            PublisherProperties properties,
            PublicationMetrics metrics,
            List<PrePublishHook> prePublishHooks,
            MediaPublishSupport mediaPublishSupport,
            ChannelAccessResolver channelAccessResolver,
            ObjectMapper objectMapper) {
        this.lifecycleJdbc = lifecycleJdbc;
        this.outboxJdbc = outboxJdbc;
        this.remotePostRegistryJdbc = remotePostRegistryJdbc;
        this.mediaAssetLoadJdbc = mediaAssetLoadJdbc;
        this.scheduledActionsFactory = scheduledActionsFactory;
        this.socialAdapter = socialAdapter;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = socialCircuitBreaker;
        this.timeLimiter = socialTimeLimiter;
        this.bulkhead = socialBulkhead;
        this.executorService = publisherVirtualExecutor;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.metrics = metrics;
        this.prePublishHooks = prePublishHooks;
        this.mediaPublishSupport = mediaPublishSupport;
        this.channelAccessResolver = channelAccessResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * Process one publish command. Returns true if the AMQP message should be ack'd.
     */
    public boolean process(UUID publicationId) {
        Optional<PublicationClaim> claimed = transactionTemplate.execute(
                status -> lifecycleJdbc.claimQueued(publicationId, properties.podName(), properties.claimLockTtl()));
        if (claimed == null || claimed.isEmpty()) {
            // Duplicate delivery or already claimed — safe to ack.
            return true;
        }
        PublicationClaim claim = claimed.get();
        MDC.put("publication_id", claim.id().toString());
        MDC.put("workspace_id", claim.workspaceId().toString());
        Instant startedAt = Instant.now();
        try {
            return handleClaim(claim, startedAt);
        } finally {
            MDC.remove("publication_id");
            MDC.remove("workspace_id");
        }
    }

    private boolean handleClaim(PublicationClaim claim, Instant startedAt) {
        PostVariantContent content = toContent(claim);
        try {
            // ORD fail-closed (and any other gate) must go through handleFailure — otherwise the
            // publication stays stuck in in_progress and never becomes failed_permanent.
            for (PrePublishHook hook : prePublishHooks) {
                content = hook.beforePublish(claim, content);
            }

            ChannelContext ctx = channelAccessResolver.context(
                    claim.workspaceId(),
                    claim.channelId(),
                    claim.externalChannelId(),
                    Network.fromCode(claim.network()));

            String contentFingerprint = contentFingerprint(claim, content);

            if (claim.remotePostId() != null && !claim.remotePostId().isBlank()) {
                finishSuccess(
                        claim,
                        new PublishResult(claim.remotePostId(), null),
                        startedAt,
                        "already_remote",
                        contentFingerprint);
                return true;
            }

            content = mediaPublishSupport.withOptionsMeta(claim, content);
            contentFingerprint = contentFingerprint(claim, content);

            // findExisting only on retries / unknown prior outcome — first attempt goes straight to publish.
            if (claim.attempts() > 1) {
                Optional<PublishResult> existing = findExisting(ctx, claim, content);
                if (existing.isPresent()) {
                    finishSuccess(claim, existing.get(), startedAt, "find_existing", contentFingerprint);
                    return true;
                }
            }

            RateLimitDecision decision =
                    rateLimiter.tryAcquire(claim.network(), claim.channelId().toString(), claim.externalChannelId());
            if (!decision.allowed()) {
                Instant next = Instant.now().plus(decision.waitTime());
                transactionTemplate.executeWithoutResult(status -> {
                    lifecycleJdbc.markFailedRetryable(
                            claim.id(), claim.runAt(), next, "rate_limited", "waiting for rate limiter");
                    lifecycleJdbc.recordAttempt(
                            claim.id(),
                            claim.runAt(),
                            claim.attempts(),
                            startedAt,
                            Instant.now(),
                            429,
                            "rate_limited",
                            "{\"waitMs\":" + decision.waitTime().toMillis() + "}");
                });
                metrics.recordFailure(claim.network(), "rate_limited");
                return true;
            }

            // BE-23/BE-28: options + mediaIds → meta; upload or inline-media enrich before publish.
            content = mediaPublishSupport.enrich(claim, content, socialAdapter, ctx);
            contentFingerprint = contentFingerprint(claim, content);

            // BE-33 L2: record publish intent before the social call for own-post recognition.
            String fingerprint = contentFingerprint;
            Instant intentExpires = Instant.now().plus(Duration.ofHours(1));
            transactionTemplate.executeWithoutResult(status -> remotePostRegistryJdbc.insertIntent(
                    claim.channelId(), claim.idempotencyKey(), fingerprint, intentExpires));

            PublishResult result = invokeSocial(ctx, content, claim);
            mediaPublishSupport.cacheUploadedFromPublish(claim.channelId(), result);
            finishSuccess(claim, result, startedAt, "published", fingerprint);
            return true;
        } catch (Exception ex) {
            return handleFailure(claim, startedAt, ex);
        }
    }

    private Optional<PublishResult> findExisting(
            ChannelContext ctx, PublicationClaim claim, PostVariantContent content) {
        if ("story".equals(claim.contentKind())) {
            return socialAdapter.findExistingStory(ctx, claim.idempotencyKey(), content);
        }
        return socialAdapter.findExisting(ctx, claim.idempotencyKey(), content);
    }

    private PublishResult invokeSocial(ChannelContext ctx, PostVariantContent content, PublicationClaim claim)
            throws Exception {
        Callable<PublishResult> publishCall = () -> {
            if ("story".equals(claim.contentKind())) {
                return socialAdapter.publishStory(ctx, content, claim.idempotencyKey());
            }
            return socialAdapter.publish(ctx, content, claim.idempotencyKey());
        };
        Callable<PublishResult> withBulkhead = Bulkhead.decorateCallable(bulkhead, publishCall);
        Callable<PublishResult> withCb = CircuitBreaker.decorateCallable(circuitBreaker, withBulkhead);
        Callable<PublishResult> withTimeout =
                () -> timeLimiter.executeFutureSupplier(() -> CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return withCb.call();
                            } catch (Exception e) {
                                if (e instanceof RuntimeException runtime) {
                                    throw runtime;
                                }
                                throw new RuntimeException(e);
                            }
                        },
                        executorService));
        return withTimeout.call();
    }

    private boolean handleFailure(PublicationClaim claim, Instant startedAt, Exception ex) {
        Throwable root = unwrap(ex);
        ErrorClass errorClass = classify(root);
        Integer httpStatus =
                root instanceof SocialApiException social ? social.httpStatus().orElse(null) : null;
        String errorCode = errorClass.name().toLowerCase(Locale.ROOT);
        String message = root.getMessage() == null ? root.toString() : root.getMessage();

        if (errorClass == ErrorClass.RATE_LIMITED) {
            Duration retryAfter = root instanceof SocialApiException social
                    ? social.retryAfter().orElse(Duration.ofSeconds(5))
                    : Duration.ofSeconds(5);
            // Short 429: wait via next_attempt_at only. Long flood / VK error 9 → AIMD key block
            // (02-backend.md §5.4: flood control → block key up to 1 h).
            if ("vk".equals(claim.network()) && retryAfter.compareTo(Duration.ofMinutes(5)) >= 0) {
                rateLimiter.penalize(
                        claim.network(), claim.channelId().toString(), claim.externalChannelId(), Duration.ofHours(1));
            } else if (retryAfter.compareTo(Duration.ofSeconds(30)) >= 0) {
                rateLimiter.penalize(
                        claim.network(), claim.channelId().toString(), claim.externalChannelId(), retryAfter);
            }
        }

        if (errorClass == ErrorClass.UNKNOWN_OUTCOME) {
            ChannelContext ctx = channelAccessResolver.context(
                    claim.workspaceId(),
                    claim.channelId(),
                    claim.externalChannelId(),
                    Network.fromCode(claim.network()));
            Optional<PublishResult> recovered =
                    findExisting(ctx, claim, mediaPublishSupport.withOptionsMeta(claim, toContent(claim)));
            if (recovered.isPresent()) {
                finishSuccess(
                        claim,
                        recovered.get(),
                        startedAt,
                        "recovered_unknown_outcome",
                        contentFingerprint(claim, toContent(claim)));
                return true;
            }
            errorClass = ErrorClass.RETRYABLE;
            errorCode = "unknown_outcome";
        }

        if (errorClass == ErrorClass.AUTH || errorClass == ErrorClass.PERMANENT) {
            ErrorClass finalClass = errorClass;
            String finalCode = errorCode;
            boolean storyWaiting = finalClass == ErrorClass.AUTH && "story".equals(claim.contentKind());
            transactionTemplate.executeWithoutResult(status -> {
                if (storyWaiting) {
                    // BE-16 / TC-STORY-20: revoked stories scope → waiting_for_channel (retry after reconnect).
                    lifecycleJdbc.markWaitingForChannel(claim.id(), claim.runAt(), finalCode, message);
                    lifecycleJdbc.markStoryConnectionRevoked(claim.channelId());
                } else {
                    lifecycleJdbc.markFailedPermanent(claim.id(), claim.runAt(), finalCode, message);
                }
                lifecycleJdbc.recordAttempt(
                        claim.id(),
                        claim.runAt(),
                        claim.attempts(),
                        startedAt,
                        Instant.now(),
                        httpStatus,
                        finalCode,
                        excerpt(message));
                if (finalClass == ErrorClass.AUTH) {
                    lifecycleJdbc.markChannelReauthRequired(claim.channelId());
                    outboxJdbc.append(
                            "channel",
                            claim.channelId(),
                            "channel.reauth_required",
                            "{\"workspaceId\":\""
                                    + claim.workspaceId()
                                    + "\",\"channelId\":\""
                                    + claim.channelId()
                                    + "\",\"network\":\""
                                    + claim.network()
                                    + "\",\"reason\":\""
                                    + finalCode
                                    + "\",\"publicationId\":\""
                                    + claim.id()
                                    + "\"}");
                }
                outboxJdbc.append(
                        "publication",
                        claim.id(),
                        storyWaiting ? "publication.waiting_for_channel" : "publication.failed",
                        "{\"network\":\""
                                + claim.network()
                                + "\",\"result\":\""
                                + finalCode
                                + "\",\"workspaceId\":\""
                                + claim.workspaceId()
                                + "\"}");
            });
            metrics.recordFailure(
                    claim.network(),
                    storyWaiting ? "waiting_for_channel" : finalClass.name().toLowerCase(Locale.ROOT));
            return true;
        }

        Instant now = Instant.now();
        Duration retryAfter =
                root instanceof SocialApiException social ? social.retryAfter().orElse(null) : null;
        if (PublicationBackoff.exhausted(
                claim.attempts(), claim.runAt(), now, properties.maxAttempts(), properties.maxAgeAfterRunAt())) {
            transactionTemplate.executeWithoutResult(status -> {
                lifecycleJdbc.markFailedPermanent(claim.id(), claim.runAt(), "attempts_exhausted", message);
                lifecycleJdbc.recordAttempt(
                        claim.id(),
                        claim.runAt(),
                        claim.attempts(),
                        startedAt,
                        Instant.now(),
                        httpStatus,
                        "attempts_exhausted",
                        excerpt(message));
                outboxJdbc.append(
                        "publication",
                        claim.id(),
                        "publication.failed",
                        "{\"network\":\""
                                + claim.network()
                                + "\",\"result\":\"attempts_exhausted\",\"workspaceId\":\""
                                + claim.workspaceId()
                                + "\"}");
            });
            metrics.recordFailure(claim.network(), "attempts_exhausted");
            return true;
        }

        Instant nextAttempt = PublicationBackoff.nextAttemptAt(claim.attempts(), now, retryAfter);
        String finalCode = errorCode;
        transactionTemplate.executeWithoutResult(status -> {
            lifecycleJdbc.markFailedRetryable(claim.id(), claim.runAt(), nextAttempt, finalCode, message);
            lifecycleJdbc.recordAttempt(
                    claim.id(),
                    claim.runAt(),
                    claim.attempts(),
                    startedAt,
                    Instant.now(),
                    httpStatus,
                    finalCode,
                    excerpt(message));
        });
        metrics.recordFailure(claim.network(), "retryable");
        log.info(
                "Publication {} attempt {} failed ({}) — next at {}",
                claim.id(),
                claim.attempts(),
                finalCode,
                nextAttempt);
        return true;
    }

    private void finishSuccess(
            PublicationClaim claim, PublishResult result, Instant startedAt, String path, String contentFingerprint) {
        String metaJson = successMetaJson(claim);
        String fingerprint = contentFingerprint;
        transactionTemplate.executeWithoutResult(status -> {
            lifecycleJdbc.markSucceeded(claim.id(), claim.runAt(), result.remotePostId(), result.remoteUrl(), metaJson);
            lifecycleJdbc.recordAttempt(
                    claim.id(),
                    claim.runAt(),
                    claim.attempts(),
                    startedAt,
                    Instant.now(),
                    200,
                    path,
                    "{\"remotePostId\":\"" + result.remotePostId() + "\"}");
            scheduledActionsFactory.createAfterSuccess(claim, result.remotePostId());
            if (result.remotePostId() != null && !result.remotePostId().isBlank()) {
                String origin = "crosspost".equals(claim.source()) ? "crosspost" : "publication";
                short hop = 0;
                String rootOriginJson = null;
                if ("crosspost".equals(origin)
                        && claim.sourceRefJson() != null
                        && !claim.sourceRefJson().isBlank()) {
                    hop = 1;
                    rootOriginJson = extractRootOriginJson(claim.sourceRefJson());
                    hop = extractHop(claim.sourceRefJson(), hop);
                }
                remotePostRegistryJdbc.upsert(
                        claim.workspaceId(),
                        claim.network(),
                        claim.channelId(),
                        result.remotePostId(),
                        origin,
                        claim.id(),
                        claim.postId(),
                        rootOriginJson,
                        hop,
                        fingerprint);
                if (claim.idempotencyKey() != null && !claim.idempotencyKey().isBlank()) {
                    remotePostRegistryJdbc.consumeIntent(claim.channelId(), claim.idempotencyKey());
                }
            }
            outboxJdbc.append(
                    "publication",
                    claim.id(),
                    "publication.succeeded",
                    "{\"network\":\""
                            + claim.network()
                            + "\",\"remotePostId\":\""
                            + result.remotePostId()
                            + "\",\"workspaceId\":\""
                            + claim.workspaceId()
                            + "\",\"contentKind\":\""
                            + (claim.contentKind() == null ? "post" : claim.contentKind())
                            + "\",\"source\":\""
                            + (claim.source() == null ? "manual" : claim.source())
                            + "\"}");
        });
        metrics.recordSuccess(claim.network(), claim.runAt(), claim.contentKind());
    }

    private String contentFingerprint(PublicationClaim claim, PostVariantContent content) {
        String text = content == null || content.text() == null ? claim.contentText() : content.text();
        List<String> mediaShas = mediaSha256List(claim);
        return ContentFingerprint.fingerprint(text, mediaShas);
    }

    private String extractRootOriginJson(String sourceRefJson) {
        try {
            JsonNode root = objectMapper.readTree(sourceRefJson).path("root_origin");
            if (root.isMissingNode() || root.isNull()) {
                return null;
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            return null;
        }
    }

    private short extractHop(String sourceRefJson, short fallback) {
        try {
            JsonNode hop = objectMapper.readTree(sourceRefJson).path("hop");
            if (hop.isNumber()) {
                return hop.shortValue();
            }
        } catch (Exception ignored) {
            // fallback
        }
        return fallback;
    }

    private List<String> mediaSha256List(PublicationClaim claim) {
        List<UUID> mediaIds = parseMediaIds(claim.mediaIdsJson());
        if (mediaIds.isEmpty()) {
            return List.of();
        }
        List<MediaAssetLoadJdbc.MediaAssetRow> rows = mediaAssetLoadJdbc.findReady(claim.workspaceId(), mediaIds);
        List<String> shas = new ArrayList<>(rows.size());
        for (MediaAssetLoadJdbc.MediaAssetRow row : rows) {
            if (row.sha256() != null && !row.sha256().isBlank()) {
                shas.add(row.sha256());
            }
        }
        return shas;
    }

    private List<UUID> parseMediaIds(String mediaIdsJson) {
        if (mediaIdsJson == null || mediaIdsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(mediaIdsJson);
            if (!node.isArray()) {
                return List.of();
            }
            List<UUID> ids = new ArrayList<>();
            for (JsonNode item : node) {
                String raw = item.asText(null);
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                try {
                    ids.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                    // skip malformed id
                }
            }
            return ids;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static PostVariantContent toContent(PublicationClaim claim) {
        // Options / media are enriched in handleClaim via MediaPublishSupport (BE-23).
        return new PostVariantContent(
                claim.contentText(),
                claim.mediaKind(),
                claim.storyTtl(),
                claim.storyLinkText(),
                claim.storyLinkUrl(),
                java.util.Map.of());
    }

    /** OK Moments live ~24h regardless of selected TTL — persist effective_ttl in publication.meta. */
    private static String successMetaJson(PublicationClaim claim) {
        if (!"story".equals(claim.contentKind())) {
            return null;
        }
        String ttl = claim.storyTtl() == null || claim.storyTtl().isBlank() ? "ttl_24h" : claim.storyTtl();
        String effective = "ok".equals(claim.network()) ? "ttl_24h" : ttl;
        return "{\"contentKind\":\"story\",\"storyTtl\":\"" + ttl + "\",\"effective_ttl\":\"" + effective + "\"}";
    }

    private ErrorClass classify(Throwable error) {
        if (error instanceof CallNotPermittedException || error instanceof BulkheadFullException) {
            return ErrorClass.RETRYABLE;
        }
        if (error instanceof TimeoutException) {
            return ErrorClass.UNKNOWN_OUTCOME;
        }
        return socialAdapter.classifyError(error);
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

    private static String excerpt(String message) {
        String safe = message == null ? "" : message.replace("\"", "'");
        if (safe.length() > 400) {
            safe = safe.substring(0, 400);
        }
        return "{\"message\":\"" + safe + "\"}";
    }
}
