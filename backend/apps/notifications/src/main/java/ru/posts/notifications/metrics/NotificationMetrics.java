package ru.posts.notifications.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer processingTimer;

    public NotificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.processingTimer = Timer.builder("posts_notifications_processing_seconds")
                .description("Domain event processing time")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public void record(String eventType, String channel, String result) {
        String type = eventType == null || eventType.isBlank() ? "unknown" : eventType;
        String ch = channel == null || channel.isBlank() ? "none" : channel;
        String res = result == null || result.isBlank() ? "unknown" : result;
        meterRegistry
                .counter("posts_notifications_total", "event_type", type, "channel", ch, "result", res)
                .increment();
    }

    public void recordProcessing(Duration duration) {
        if (duration != null && !duration.isNegative()) {
            processingTimer.record(duration);
        }
    }

    public Timer.Sample startSample() {
        return Timer.start(meterRegistry);
    }

    public void stopSample(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(processingTimer);
        }
    }
}
