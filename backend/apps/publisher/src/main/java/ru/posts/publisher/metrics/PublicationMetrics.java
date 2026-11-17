package ru.posts.publisher.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class PublicationMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer delayTimer;

    public PublicationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.delayTimer = Timer.builder("posts_publication_delay_seconds")
                .description("Delay from run_at to successful publish")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public void recordSuccess(String network, Instant runAt) {
        meterRegistry
                .counter("posts_publications_total", "network", network, "result", "success")
                .increment();
        if (runAt != null) {
            Duration delay = Duration.between(runAt, Instant.now());
            if (!delay.isNegative()) {
                delayTimer.record(delay);
            }
        }
    }

    public void recordFailure(String network, String result) {
        meterRegistry
                .counter("posts_publications_total", "network", network, "result", result)
                .increment();
    }
}
