package ru.posts.ratelimit;

import java.time.Duration;

/**
 * Distributed (Bucket4j/Redis) rate limiter for social API calls with local Resilience4j fallback.
 */
public interface SocialRateLimiter {

    RateLimitDecision tryAcquire(String network, String socialAccountId, String externalChatId);

    /**
     * AIMD penalty after 429 / flood control (02-backend.md §5.4). Temporarily blocks token/chat
     * keys for at least {@code blockFor} (VK flood → up to 1 h).
     */
    default void penalize(String network, String socialAccountId, String externalChatId, Duration blockFor) {
        // no-op for allow-all / tests
    }
}
