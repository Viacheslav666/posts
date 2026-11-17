package ru.posts.ratelimit;

/** No-op limiter for local load tests / when Redis limits are intentionally disabled. */
public final class AllowAllSocialRateLimiter implements SocialRateLimiter {

    @Override
    public RateLimitDecision tryAcquire(String network, String socialAccountId, String externalChatId) {
        return RateLimitDecision.allow();
    }
}
