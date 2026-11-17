package ru.posts.ratelimit;

/**
 * Distributed (Bucket4j/Redis) rate limiter for social API calls with local Resilience4j fallback.
 */
public interface SocialRateLimiter {

    RateLimitDecision tryAcquire(String network, String socialAccountId, String externalChatId);
}
