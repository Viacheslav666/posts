package ru.posts.scheduler.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Scheduler gauges for SchedulerStalled / queue recovery alerts (04-devops §6). */
@Component
public class SchedulerMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicLong lastDispatchAgeSeconds = new AtomicLong(0);

    public SchedulerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("posts_scheduler_last_dispatch_age_seconds", lastDispatchAgeSeconds);
    }

    public void recordDispatched(int count) {
        if (count > 0) {
            meterRegistry.counter("posts_scheduler_dispatched_total").increment(count);
        }
        lastDispatchAgeSeconds.set(0);
    }

    public void recordDispatchIdle() {
        lastDispatchAgeSeconds.incrementAndGet();
    }

    public void recordActionsDispatched(int count) {
        if (count > 0) {
            meterRegistry.counter("posts_scheduler_actions_dispatched_total").increment(count);
        }
    }

    public void recordModerationActionsDispatched(int count) {
        if (count > 0) {
            meterRegistry
                    .counter("posts_scheduler_moderation_actions_dispatched_total")
                    .increment(count);
        }
    }

    public void recordConfirmFailures(int count) {
        meterRegistry.counter("posts_scheduler_confirm_failures_total").increment(count);
    }

    public void recordReaper(int count) {
        meterRegistry.counter("posts_scheduler_reaper_total").increment(count);
    }
}
