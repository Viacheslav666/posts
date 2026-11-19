package ru.posts.scheduler.dispatch;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.posts.persistence.jdbc.PublicationPartitions;

/**
 * Extends monthly partitions for {@code publications} and {@code social_remote_actions}.
 * Migrations only create them through 2027-09.
 */
@Component
public class PublicationPartitionJob {

    private static final Logger log = LoggerFactory.getLogger(PublicationPartitionJob.class);
    private static final int AHEAD_MONTHS = 18;

    private final JdbcTemplate jdbc;

    public PublicationPartitionJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${posts.publications.partition-cron:0 20 3 1 * *}", zone = "UTC")
    @SchedulerLock(name = "publicationPartitions", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public void maintain() {
        YearMonth start = YearMonth.now(ZoneOffset.UTC);
        for (int i = 0; i <= AHEAD_MONTHS; i++) {
            Instant sample = start.plusMonths(i).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            PublicationPartitions.ensureMonth(jdbc, "publications", sample);
            PublicationPartitions.ensureMonth(jdbc, "social_remote_actions", sample);
        }
        log.info("publication_partitions_ensured from={} aheadMonths={}", start, AHEAD_MONTHS);
    }
}
