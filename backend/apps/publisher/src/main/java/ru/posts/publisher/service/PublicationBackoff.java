package ru.posts.publisher.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Exponential-ish backoff schedule from 02-backend.md §5.3. */
public final class PublicationBackoff {

    private static final List<Duration> STEPS = List.of(
            Duration.ofSeconds(5),
            Duration.ofSeconds(15),
            Duration.ofSeconds(45),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5),
            Duration.ofMinutes(10),
            Duration.ofMinutes(15));

    private PublicationBackoff() {}

    public static Instant nextAttemptAt(int attemptNo, Instant now, Duration retryAfterFloor) {
        int index = Math.max(0, Math.min(attemptNo - 1, STEPS.size() - 1));
        Duration backoff = STEPS.get(index);
        if (retryAfterFloor != null && retryAfterFloor.compareTo(backoff) > 0) {
            backoff = retryAfterFloor;
        }
        return now.plus(backoff);
    }

    public static boolean exhausted(int attempts, Instant runAt, Instant now, int maxAttempts, Duration maxAge) {
        if (attempts >= maxAttempts) {
            return true;
        }
        return runAt != null && now.isAfter(runAt.plus(maxAge));
    }
}
