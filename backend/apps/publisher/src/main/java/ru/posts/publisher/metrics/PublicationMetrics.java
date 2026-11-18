package ru.posts.publisher.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Micrometer metrics for publishing dashboards / alerts (04-devops.md §6). */
@Component
public class PublicationMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer delayTimer;
    private final AtomicLong retryableGauge = new AtomicLong();
    private final AtomicLong permanentGauge = new AtomicLong();

    public PublicationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.delayTimer = Timer.builder("posts_publication_delay_seconds")
                .description("Delay from run_at to successful publish")
                .publishPercentileHistogram()
                .register(meterRegistry);
        meterRegistry.gauge("posts_publications_retryable", retryableGauge);
        meterRegistry.gauge("posts_publications_failed_permanent", permanentGauge);
    }

    public void recordSuccess(String network, Instant runAt) {
        recordSuccess(network, runAt, "post");
    }

    public void recordSuccess(String network, Instant runAt, String contentKind) {
        String kind = contentKind == null || contentKind.isBlank() ? "post" : contentKind;
        meterRegistry
                .counter("posts_publications_total", "network", network, "result", "success", "content_type", kind)
                .increment();
        if (runAt != null) {
            Duration delay = Duration.between(runAt, Instant.now());
            if (!delay.isNegative()) {
                Timer.builder("posts_publication_delay_seconds")
                        .tag("network", network)
                        .tag("content_type", kind)
                        .publishPercentileHistogram()
                        .register(meterRegistry)
                        .record(delay);
                delayTimer.record(delay);
            }
        }
    }

    public void recordFailure(String network, String result) {
        meterRegistry
                .counter("posts_publications_total", "network", network, "result", result)
                .increment();
        if ("retryable".equals(result) || "rate_limited".equals(result)) {
            meterRegistry
                    .counter("posts_publication_retries_total", "network", network)
                    .increment();
            retryableGauge.incrementAndGet();
        }
        if ("auth".equals(result) || "permanent".equals(result) || "attempts_exhausted".equals(result)) {
            permanentGauge.incrementAndGet();
        }
    }

    public void recordAction(String network, String kind, String result) {
        meterRegistry
                .counter("posts_scheduled_actions_total", "network", network, "kind", kind, "result", result)
                .increment();
    }

    public void recordDlq(String network, String queueKind) {
        meterRegistry
                .counter("posts_pipeline_dlq_total", "network", network, "queue", queueKind)
                .increment();
    }
}
