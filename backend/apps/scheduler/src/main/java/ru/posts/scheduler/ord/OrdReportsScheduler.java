package ru.posts.scheduler.ord;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.posts.ord.reporting.OrdCreativeLifecycleService;
import ru.posts.ord.reporting.OrdReportMetrics;
import ru.posts.ord.reporting.OrdReportRunner;
import ru.posts.persistence.entity.OrdConnectionEntity;
import ru.posts.persistence.entity.OrdReportRunEntity;
import ru.posts.persistence.repository.OrdConnectionRepository;
import ru.posts.persistence.repository.OrdReportRunRepository;

/**
 * Monthly ORD acts/statistics job (parity with vposter: ~7th day 03:00 Europe/Moscow) plus overdue
 * alerts and expiry warnings (BE-26).
 */
@Component
public class OrdReportsScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrdReportsScheduler.class);
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final OrdConnectionRepository connectionRepository;
    private final OrdReportRunRepository reportRunRepository;
    private final OrdReportRunner reportRunner;
    private final OrdCreativeLifecycleService lifecycleService;
    private final OrdReportMetrics metrics;

    public OrdReportsScheduler(
            OrdConnectionRepository connectionRepository,
            OrdReportRunRepository reportRunRepository,
            OrdReportRunner reportRunner,
            OrdCreativeLifecycleService lifecycleService,
            OrdReportMetrics metrics) {
        this.connectionRepository = connectionRepository;
        this.reportRunRepository = reportRunRepository;
        this.reportRunner = reportRunner;
        this.lifecycleService = lifecycleService;
        this.metrics = metrics;
    }

    @Scheduled(cron = "${posts.ord.reports.cron:0 0 3 7 * *}", zone = "Europe/Moscow")
    @SchedulerLock(name = "ordMonthlyReports", lockAtMostFor = "2h", lockAtLeastFor = "1m")
    public void runMonthlyReports() {
        LocalDate previousMonth = LocalDate.now(MOSCOW).minusMonths(1).withDayOfMonth(1);
        int year = previousMonth.getYear();
        int month = previousMonth.getMonthValue();

        List<OrdConnectionEntity> connections = connectionRepository.findAll().stream()
                .filter(c -> "active".equals(c.getStatus()))
                .toList();

        int ok = 0;
        int failed = 0;
        for (OrdConnectionEntity connection : connections) {
            try {
                reportRunner.run(connection.getWorkspaceId(), connection.getProvider(), year, month);
                ok++;
            } catch (RuntimeException ex) {
                failed++;
                log.warn(
                        "ORD monthly report failed workspace={} provider={} period={}-{}: {}",
                        connection.getWorkspaceId(),
                        connection.getProvider(),
                        year,
                        month,
                        ex.toString());
            }
        }
        log.info(
                "ORD monthly report job done: period={}-{}, connections={}, ok={}, failed={}",
                year,
                month,
                connections.size(),
                ok,
                failed);
    }

    @Scheduled(cron = "${posts.ord.reports.retry-cron:0 30 * * * *}", zone = "Europe/Moscow")
    @SchedulerLock(name = "ordReportRetries", lockAtMostFor = "30m", lockAtLeastFor = "30s")
    public void retryFailedReports() {
        Instant now = Instant.now();
        for (OrdReportRunEntity run : reportRunRepository.findDueRetries(now)) {
            try {
                reportRunner.run(run.getWorkspaceId(), run.getProvider(), run.getPeriodYear(), run.getPeriodMonth());
            } catch (RuntimeException ex) {
                log.warn("ORD report retry failed runId={}: {}", run.getId(), ex.toString());
            }
        }
    }

    /** Alert when previous-month reports are still incomplete after the 25th (MSK). */
    @Scheduled(cron = "${posts.ord.reports.overdue-cron:0 0 9 25 * *}", zone = "Europe/Moscow")
    @SchedulerLock(name = "ordReportOverdueAlerts", lockAtMostFor = "15m", lockAtLeastFor = "30s")
    public void alertOverdueReports() {
        LocalDate previousMonth = LocalDate.now(MOSCOW).minusMonths(1).withDayOfMonth(1);
        int year = previousMonth.getYear();
        int month = previousMonth.getMonthValue();
        for (OrdReportRunEntity run : reportRunRepository.findIncompleteForPeriod(year, month)) {
            metrics.recordOverdue(run.getProvider());
            log.warn(
                    "ORD report overdue workspace={} provider={} period={}-{} status={}",
                    run.getWorkspaceId(),
                    run.getProvider(),
                    year,
                    month,
                    run.getStatus());
        }
        // Also flag active connections with no run row at all for the period.
        for (OrdConnectionEntity connection : connectionRepository.findAll()) {
            if (!"active".equals(connection.getStatus())) {
                continue;
            }
            boolean present = reportRunRepository
                    .findByWorkspaceIdAndProviderAndPeriodYearAndPeriodMonth(
                            connection.getWorkspaceId(), connection.getProvider(), year, month)
                    .isPresent();
            if (!present) {
                metrics.recordOverdue(connection.getProvider());
            }
        }
    }

    @Scheduled(cron = "${posts.ord.reports.expiry-cron:0 15 8 * * *}", zone = "Europe/Moscow")
    @SchedulerLock(name = "ordExpiryWarnings", lockAtMostFor = "15m", lockAtLeastFor = "30s")
    public void sendExpiryWarnings() {
        int sent = lifecycleService.sendExpiryWarnings(Instant.now());
        if (sent > 0) {
            log.info("ORD expiry warnings sent={}", sent);
        }
    }
}
