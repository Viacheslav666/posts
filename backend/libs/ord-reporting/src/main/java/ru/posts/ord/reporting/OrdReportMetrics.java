package ru.posts.ord.reporting;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Prometheus metrics {@code ord_report_*}. */
@Profile("!openapi")
@Component
public class OrdReportMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer runTimer;

    public OrdReportMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.runTimer = Timer.builder("ord_report_run_seconds")
                .description("ORD monthly/manual report run duration")
                .register(meterRegistry);
    }

    public void recordRun(String provider, String status, long durationMs, int invoices, int statistics) {
        runTimer.record(durationMs, TimeUnit.MILLISECONDS);
        Counter.builder("ord_report_runs_total")
                .tag("provider", provider)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
        Counter.builder("ord_report_invoices_total")
                .tag("provider", provider)
                .tag("status", status)
                .register(meterRegistry)
                .increment(invoices);
        Counter.builder("ord_report_statistics_total")
                .tag("provider", provider)
                .tag("status", status)
                .register(meterRegistry)
                .increment(statistics);
    }

    public void recordError(String provider, String reason) {
        Counter.builder("ord_report_errors_total")
                .tag("provider", provider)
                .tag("reason", reason == null ? "unknown" : reason)
                .register(meterRegistry)
                .increment();
    }

    public void recordOverdue(String provider) {
        Counter.builder("ord_report_overdue_total")
                .tag("provider", provider)
                .register(meterRegistry)
                .increment();
    }

    public void recordExpiryWarning() {
        Counter.builder("ord_report_expiry_warnings_total")
                .register(meterRegistry)
                .increment();
    }
}
