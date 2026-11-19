package ru.posts.scheduler.inbound;

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

/**
 * Creates future daily partitions for {@code social_inbound_events} and drops partitions older than
 * retention (BE-32).
 */
@Component
public class InboundPartitionJob {

    private static final Logger log = LoggerFactory.getLogger(InboundPartitionJob.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy_MM_dd");

    private final JdbcTemplate jdbc;
    private final int retentionDays;
    private final int aheadDays;

    public InboundPartitionJob(
            JdbcTemplate jdbc,
            @Value("${posts.social-inbound.retention-days:14}") int retentionDays,
            @Value("${posts.social-inbound.partition-ahead-days:7}") int aheadDays) {
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
        this.aheadDays = aheadDays;
    }

    @Scheduled(cron = "${posts.social-inbound.partition-cron:0 15 3 * * *}", zone = "UTC")
    @SchedulerLock(name = "socialInboundPartitions", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public void maintain() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = 0; i <= aheadDays; i++) {
            ensurePartition(today.plusDays(i));
        }
        LocalDate dropBefore = today.minusDays(retentionDays);
        // Drop known old partitions by scanning pg_inherits names matching prefix
        jdbc.query(
                        """
                        SELECT c.relname
                        FROM pg_inherits i
                        JOIN pg_class c ON c.oid = i.inhrelid
                        JOIN pg_class p ON p.oid = i.inhparent
                        WHERE p.relname = 'social_inbound_events'
                        """,
                        (rs, rowNum) -> rs.getString(1))
                .forEach(name -> maybeDrop(name, dropBefore));
    }

    private void ensurePartition(LocalDate day) {
        String name = "social_inbound_events_" + DAY.format(day);
        String from = day.toString();
        String to = day.plusDays(1).toString();
        jdbc.execute("CREATE TABLE IF NOT EXISTS "
                + name
                + " PARTITION OF social_inbound_events FOR VALUES FROM ('"
                + from
                + "+00') TO ('"
                + to
                + "+00')");
    }

    private void maybeDrop(String partitionName, LocalDate dropBefore) {
        String prefix = "social_inbound_events_";
        if (!partitionName.startsWith(prefix)) {
            return;
        }
        String suffix = partitionName.substring(prefix.length());
        try {
            LocalDate day = LocalDate.parse(suffix, DAY);
            if (day.isBefore(dropBefore)) {
                log.info("dropping_social_inbound_partition name={}", partitionName);
                jdbc.execute("DROP TABLE IF EXISTS " + partitionName);
            }
        } catch (RuntimeException ex) {
            log.debug("skip_partition_drop name={} err={}", partitionName, ex.toString());
        }
    }
}
