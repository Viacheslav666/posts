package ru.posts.ratelimit;

import java.time.Duration;

/** Result of trying to consume a social API rate-limit token. */
public record RateLimitDecision(boolean allowed, Duration waitTime) {

    public static RateLimitDecision allow() {
        return new RateLimitDecision(true, Duration.ZERO);
    }

    public static RateLimitDecision deny(Duration waitTime) {
        return new RateLimitDecision(false, waitTime == null ? Duration.ofSeconds(1) : waitTime);
    }
}
