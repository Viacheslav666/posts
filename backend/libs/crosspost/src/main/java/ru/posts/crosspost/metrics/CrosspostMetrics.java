package ru.posts.crosspost.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Profile("!openapi")
@Component
public class CrosspostMetrics {

    private final MeterRegistry registry;
    private final Timer lagTimer;

    public CrosspostMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        MeterRegistry found = registryProvider.getIfAvailable();
        this.registry = found != null ? found : new SimpleMeterRegistry();
        this.lagTimer = Timer.builder("crosspost_source_to_copy_seconds")
                .publishPercentileHistogram()
                .register(this.registry);
    }

    public void itemStatus(String status, String reason) {
        Counter.builder("crosspost_items_total")
                .tag("status", status == null ? "unknown" : status)
                .tag("reason", reason == null || reason.isBlank() ? "none" : reason)
                .register(registry)
                .increment();
    }

    public void loopBlocked(String layer) {
        Counter.builder("crosspost_loop_blocked_total")
                .tag("layer", layer == null ? "unknown" : layer)
                .register(registry)
                .increment();
    }

    public void recordLag(Instant sourceOccurredAt) {
        if (sourceOccurredAt == null) {
            return;
        }
        long ms = Math.max(0, Duration.between(sourceOccurredAt, Instant.now()).toMillis());
        lagTimer.record(ms, TimeUnit.MILLISECONDS);
    }
}
