package ru.posts.notifications.pipeline;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Backoff schedule aligned with publication retries (5s,15s,45s,2m,5m,10m,15m). */
public final class DeliveryBackoff {

    private static final List<Duration> STEPS = List.of(
            Duration.ofSeconds(5),
            Duration.ofSeconds(15),
            Duration.ofSeconds(45),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5),
            Duration.ofMinutes(10),
            Duration.ofMinutes(15));

    private DeliveryBackoff() {}

    public static Instant nextAttemptAt(int attemptNo, Instant now) {
        int index = Math.max(0, Math.min(attemptNo - 1, STEPS.size() - 1));
        return now.plus(STEPS.get(index));
    }

    public static boolean exhausted(int attempts, int maxAttempts) {
        return attempts >= maxAttempts;
    }
}
