package ru.posts.scheduler.antispam;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Creates future monthly partitions for {@code antispam_log} and drops ones older than retention. */
@Component
public class AntispamPartitionJob {

    private static final Logger log = LoggerFactory.getLogger(AntispamPartitionJob.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy_MM");

    private final JdbcTemplate jdbc;
    private final int retentionDays;
    private final int aheadMonths;

    public AntispamPartitionJob(
            JdbcTemplate jdbc,
            @Value("${posts.antispam.retention-days:90}") int retentionDays,
            @Value("${posts.antispam.partition-ahead-months:2}") int aheadMonths) {
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
        this.aheadMonths = aheadMonths;
    }

    @Scheduled(cron = "${posts.antispam.partition-cron:0 25 3 1 * *}", zone = "UTC")
    @SchedulerLock(name = "antispamLogPartitions", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public void maintain() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        for (int i = 0; i <= aheadMonths; i++) {
            ensurePartition(today.plusMonths(i));
        }
        LocalDate dropBefore = LocalDate.now(ZoneOffset.UTC).minusDays(retentionDays).withDayOfMonth(1);
        jdbc.query(
                        """
                        SELECT c.relname
                        FROM pg_inherits i
                        JOIN pg_class c ON c.oid = i.inhrelid
                        JOIN pg_class p ON p.oid = i.inhparent
                        WHERE p.relname = 'antispam_log'
                        """,
                        (rs, rowNum) -> rs.getString(1))
                .forEach(name -> maybeDrop(name, dropBefore));
    }

    private void ensurePartition(LocalDate monthStart) {
        String name = "antispam_log_" + MONTH.format(monthStart);
        String from = monthStart.toString();
        String to = monthStart.plusMonths(1).toString();
        jdbc.execute("CREATE TABLE IF NOT EXISTS "
                + name
                + " PARTITION OF antispam_log FOR VALUES FROM ('"
                + from
                + "+00') TO ('"
                + to
                + "+00')");
    }

    private void maybeDrop(String partitionName, LocalDate dropBefore) {
        String prefix = "antispam_log_";
        if (!partitionName.startsWith(prefix)) {
            return;
        }
        String suffix = partitionName.substring(prefix.length());
        try {
            LocalDate month = LocalDate.parse(suffix + "_01", DateTimeFormatter.ofPattern("yyyy_MM_dd"));
            if (month.isBefore(dropBefore)) {
                log.info("dropping_antispam_log_partition name={}", partitionName);
                jdbc.execute("DROP TABLE IF EXISTS " + partitionName);
            }
        } catch (RuntimeException ex) {
            log.debug("skip_antispam_partition_drop name={} err={}", partitionName, ex.toString());
        }
    }
}
