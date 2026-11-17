package ru.posts.publisher.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import java.time.Duration;
import java.time.Instant;
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
import ru.posts.persistence.jdbc.PublicationClaim;
import ru.posts.persistence.jdbc.PublicationLifecycleJdbc;
import ru.posts.publisher.config.PublisherProperties;
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
    private final SocialAdapter socialAdapter;
    private final SocialRateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;
    private final ExecutorService executorService;
    private final TransactionTemplate transactionTemplate;
    private final PublisherProperties properties;
    private final PublicationMetrics metrics;

    public PublicationProcessor(
            PublicationLifecycleJdbc lifecycleJdbc,
            SocialAdapter socialAdapter,
            SocialRateLimiter rateLimiter,
            CircuitBreaker socialCircuitBreaker,
            TimeLimiter socialTimeLimiter,
            ExecutorService publisherVirtualExecutor,
            TransactionTemplate transactionTemplate,
            PublisherProperties properties,
            PublicationMetrics metrics) {
        this.lifecycleJdbc = lifecycleJdbc;
        this.socialAdapter = socialAdapter;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = socialCircuitBreaker;
        this.timeLimiter = socialTimeLimiter;
        this.executorService = publisherVirtualExecutor;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.metrics = metrics;
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
        PostVariantContent content = new PostVariantContent(claim.contentText());
        ChannelContext ctx = new ChannelContext(
                claim.workspaceId(),
                claim.channelId(),
                claim.externalChannelId(),
                "emulator-token",
                Network.fromCode(claim.network()));

        if (claim.remotePostId() != null && !claim.remotePostId().isBlank()) {
            finishSuccess(claim, new PublishResult(claim.remotePostId(), null), startedAt, "already_remote");
            return true;
        }

        // findExisting only on retries / unknown prior outcome — first attempt goes straight to publish.
        if (claim.attempts() > 1) {
            Optional<PublishResult> existing = socialAdapter.findExisting(ctx, claim.idempotencyKey(), content);
            if (existing.isPresent()) {
                finishSuccess(claim, existing.get(), startedAt, "find_existing");
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

        try {
            PublishResult result = invokeSocial(ctx, content, claim.idempotencyKey());
            finishSuccess(claim, result, startedAt, "published");
            return true;
        } catch (Exception ex) {
            return handleFailure(claim, startedAt, ex);
        }
    }

    private PublishResult invokeSocial(ChannelContext ctx, PostVariantContent content, String idempotencyKey)
            throws Exception {
        Callable<PublishResult> callable = CircuitBreaker.decorateCallable(
                circuitBreaker, () -> socialAdapter.publish(ctx, content, idempotencyKey));
        Callable<PublishResult> withTimeout =
                () -> timeLimiter.executeFutureSupplier(() -> CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return callable.call();
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

        if (errorClass == ErrorClass.UNKNOWN_OUTCOME) {
            ChannelContext ctx = new ChannelContext(
                    claim.workspaceId(),
                    claim.channelId(),
                    claim.externalChannelId(),
                    "emulator-token",
                    Network.fromCode(claim.network()));
            Optional<PublishResult> recovered = socialAdapter.findExisting(
                    ctx, claim.idempotencyKey(), new PostVariantContent(claim.contentText()));
            if (recovered.isPresent()) {
                finishSuccess(claim, recovered.get(), startedAt, "recovered_unknown_outcome");
                return true;
            }
            errorClass = ErrorClass.RETRYABLE;
            errorCode = "unknown_outcome";
        }

        if (errorClass == ErrorClass.AUTH || errorClass == ErrorClass.PERMANENT) {
            ErrorClass finalClass = errorClass;
            String finalCode = errorCode;
            transactionTemplate.executeWithoutResult(status -> {
                lifecycleJdbc.markFailedPermanent(claim.id(), claim.runAt(), finalCode, message);
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
            metrics.recordFailure(claim.network(), finalClass.name().toLowerCase(Locale.ROOT));
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

    private void finishSuccess(PublicationClaim claim, PublishResult result, Instant startedAt, String path) {
        transactionTemplate.executeWithoutResult(status -> {
            lifecycleJdbc.markSucceeded(claim.id(), claim.runAt(), result.remotePostId(), result.remoteUrl());
            lifecycleJdbc.recordAttempt(
                    claim.id(),
                    claim.runAt(),
                    claim.attempts(),
                    startedAt,
                    Instant.now(),
                    200,
                    path,
                    "{\"remotePostId\":\"" + result.remotePostId() + "\"}");
        });
        metrics.recordSuccess(claim.network(), claim.runAt());
    }

    private ErrorClass classify(Throwable error) {
        if (error instanceof CallNotPermittedException) {
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
