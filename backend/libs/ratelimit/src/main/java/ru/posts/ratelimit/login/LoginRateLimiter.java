package ru.posts.ratelimit.login;

/** Distributed/local login attempt limiter (Bucket4j). */
public interface LoginRateLimiter {

    /**
     * Consumes one login attempt for the given key.
     *
     * @return {@code true} if allowed, {@code false} if rate limited
     */
    boolean tryConsume(String key);

    void reset(String key);
}
