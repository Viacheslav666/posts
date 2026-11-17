package ru.posts.ratelimit.login;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local Bucket4j limiter: 5 attempts / minute per key (IP+email). */
public final class InMemoryLoginRateLimiter implements LoginRateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final Duration period;

    public InMemoryLoginRateLimiter() {
        this(5, Duration.ofMinutes(1));
    }

    public InMemoryLoginRateLimiter(int capacity, Duration period) {
        this.capacity = capacity;
        this.period = period;
    }

    @Override
    public boolean tryConsume(String key) {
        return buckets.computeIfAbsent(key, ignored -> newBucket()).tryConsume(1);
    }

    @Override
    public void reset(String key) {
        buckets.remove(key);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, period)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
