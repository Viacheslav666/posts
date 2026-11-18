package ru.posts.analytics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("!openapi")
@Component
public class AnalyticsPostsMetrics {

    private final Counter runs;
    private final Counter months;
    private final Counter failures;
    private final Timer duration;

    public AnalyticsPostsMetrics(MeterRegistry registry) {
        this.runs = Counter.builder("analytics_posts_job_runs_total")
                .description("Analytics posts materialisation job runs")
                .register(registry);
        this.months = Counter.builder("analytics_posts_months_recomputed_total")
                .description("Months recomputed by analytics posts job")
                .register(registry);
        this.failures = Counter.builder("analytics_posts_job_failures_total")
                .description("Analytics posts job failures")
                .register(registry);
        this.duration = Timer.builder("analytics_posts_job_duration_seconds")
                .description("Analytics posts job duration")
                .register(registry);
    }

    public void recordSuccess(int monthsRecomputed, Duration took) {
        runs.increment();
        months.increment(monthsRecomputed);
        duration.record(took);
    }

    public void recordFailure() {
        failures.increment();
    }
}
