package ru.posts.antispam.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class AntispamMetrics {

    private final MeterRegistry registry;
    private final Timer verdictLatency;
    private final Timer eventToActionLatency;

    public AntispamMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.verdictLatency = Timer.builder("antispam_verdict_latency")
                .description("AntispamEngine.evaluate latency")
                .publishPercentileHistogram()
                .register(registry);
        this.eventToActionLatency = Timer.builder("antispam_event_to_action_latency")
                .description("Inbound event received_at → remote action enqueue")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void recordVerdict(String network, String section, String reason, Duration took) {
        Counter.builder("antispam_verdicts_total")
                .tag("network", nullToUnknown(network))
                .tag("section", nullToUnknown(section))
                .tag("reason", nullToUnknown(reason))
                .register(registry)
                .increment();
        if (took != null) {
            verdictLatency.record(took.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    public void recordAction(String result) {
        Counter.builder("antispam_actions_total")
                .tag("result", nullToUnknown(result))
                .register(registry)
                .increment();
    }

    public void recordEventToAction(Duration took) {
        if (took != null) {
            eventToActionLatency.record(took.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    private static String nullToUnknown(String v) {
        return v == null || v.isBlank() ? "unknown" : v;
    }
}
