package ru.posts.social.inbound.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Micrometer metrics for social inbound pipeline (BE-32). */
public class SocialInboundMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final Timer lagTimer;

    public SocialInboundMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.lagTimer = Timer.builder("social_inbound_lag_seconds")
                .description("Hook enqueue to handler completion lag")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void recordResult(String network, String kind, String result) {
        counter("social_inbound_events_total", network, kind, result).increment();
    }

    public void recordDuplicate(String network) {
        counter("social_inbound_duplicates_total", network, "n/a", "duplicate").increment();
    }

    public void recordAuthFailure(String network) {
        counter("social_hook_auth_failures_total", network, "n/a", "auth_failed")
                .increment();
    }

    public void recordRetryCounter(String network, Integer retryCounter) {
        if (retryCounter == null) {
            return;
        }
        counter("social_inbound_vk_retry_counter", network, "n/a", String.valueOf(retryCounter))
                .increment();
    }

    public void recordLag(Instant enqueuedAt) {
        if (enqueuedAt == null) {
            return;
        }
        long nanos = Duration.between(enqueuedAt, Instant.now()).toNanos();
        lagTimer.record(Math.max(0, nanos), TimeUnit.NANOSECONDS);
    }

    private Counter counter(String name, String network, String kind, String result) {
        String key = name + "|" + network + "|" + kind + "|" + result;
        return counters.computeIfAbsent(key, k -> Counter.builder(name)
                .tag("network", network == null ? "unknown" : network)
                .tag("kind", kind == null ? "n/a" : kind)
                .tag("result", result == null ? "n/a" : result)
                .register(registry));
    }
}
