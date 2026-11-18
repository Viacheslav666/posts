package ru.posts.scheduler.ord;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.posts.common.uuid.UuidV7;

/**
 * Monthly ORD acts/statistics job (parity with vposter: ~7th day 03:00 Europe/Moscow).
 *
 * <p>Creates idempotent {@code ord_report_runs} rows per active connection for the previous calendar
 * month. Detailed provider submission is performed by API {@code POST /ord/reports/run}; this job
 * records due runs so operators/API can complete them without missing the statutory window.
 */
@Component
public class OrdReportsScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrdReportsScheduler.class);
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final JdbcClient jdbcClient;

    public OrdReportsScheduler(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Scheduled(cron = "${posts.ord.reports.cron:0 0 3 7 * *}", zone = "Europe/Moscow")
    @SchedulerLock(name = "ordMonthlyReports", lockAtMostFor = "1h", lockAtLeastFor = "1m")
    public void enqueueMonthlyReports() {
        LocalDate previousMonth = LocalDate.now(MOSCOW).minusMonths(1).withDayOfMonth(1);
        int year = previousMonth.getYear();
        int month = previousMonth.getMonthValue();

        List<ConnectionRow> connections = jdbcClient
                .sql(
                        """
                        SELECT id, workspace_id, provider
                        FROM ord_connections
                        WHERE status = 'active'
                        """)
                .query((rs, rowNum) -> new ConnectionRow(
                        rs.getObject("id", java.util.UUID.class),
                        rs.getObject("workspace_id", java.util.UUID.class),
                        rs.getString("provider")))
                .list();

        int created = 0;
        for (ConnectionRow connection : connections) {
            int inserted = jdbcClient
                    .sql(
                            """
                            INSERT INTO ord_report_runs (
                                id, workspace_id, provider, period_year, period_month,
                                status, invoices_count, statistics_count, started_at
                            ) VALUES (
                                :id, :workspaceId, :provider, :year, :month,
                                'running', 0, 0, now()
                            )
                            ON CONFLICT (workspace_id, provider, period_year, period_month) DO NOTHING
                            """)
                    .param("id", UuidV7.generate())
                    .param("workspaceId", connection.workspaceId())
                    .param("provider", connection.provider())
                    .param("year", year)
                    .param("month", month)
                    .update();
            created += inserted;
        }
        log.info(
                "ORD monthly report enqueue: period={}-{}, connections={}, newRuns={}",
                year,
                month,
                connections.size(),
                created);
    }

    private record ConnectionRow(java.util.UUID id, java.util.UUID workspaceId, String provider) {}
}
