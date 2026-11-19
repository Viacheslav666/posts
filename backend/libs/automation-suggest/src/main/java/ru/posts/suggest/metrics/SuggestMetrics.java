package ru.posts.suggest.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Micrometer meters for BE-34 suggest automation. */
@Profile("!openapi")
@Component
public class SuggestMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> statusCounters = new ConcurrentHashMap<>();
    private final Timer decisionLatency;
    private final AtomicLong autoPublish = new AtomicLong();
    private final AtomicLong totalDecisions = new AtomicLong();
    private final Counter autoPublishCounter;
    private final Counter moderatedCounter;

    public SuggestMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.decisionLatency = Timer.builder("suggest_decision_latency")
                .description("Latency of suggest filter decision")
                .register(registry);
        this.autoPublishCounter = Counter.builder("suggest_auto_publish_total")
                .description("Auto-published suggested posts")
                .register(registry);
        this.moderatedCounter = Counter.builder("suggest_moderated_total")
                .description("Suggested posts sent to moderation")
                .register(registry);
        registry.gauge("suggest_auto_publish_ratio", this, SuggestMetrics::autoPublishRatio);
    }

    public void recordStatus(String status) {
        String key = status == null ? "unknown" : status;
        statusCounters
                .computeIfAbsent(key, s -> Counter.builder("suggest_items_total")
                        .tag("status", s)
                        .register(registry))
                .increment();
    }

    public void recordDecision(Duration duration, boolean autoPublished, boolean moderated) {
        decisionLatency.record(duration == null ? Duration.ZERO : duration);
        totalDecisions.incrementAndGet();
        if (autoPublished) {
            autoPublish.incrementAndGet();
            autoPublishCounter.increment();
        }
        if (moderated) {
            moderatedCounter.increment();
        }
    }

    public double autoPublishRatio() {
        long total = totalDecisions.get();
        if (total == 0) {
            return 0d;
        }
        return (double) autoPublish.get() / (double) total;
    }
}
