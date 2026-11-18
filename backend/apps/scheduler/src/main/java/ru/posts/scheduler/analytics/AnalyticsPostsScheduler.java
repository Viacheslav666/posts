package ru.posts.scheduler.analytics;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.posts.analytics.AnalyticsPostsAggregator;

/**
 * Incremental materialisation of {@code analytics_posts_monthly} (BE-30). Distributed lock via
 * ShedLock/Redis — same pattern as ORD monthly reports.
 */
@Component
public class AnalyticsPostsScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsPostsScheduler.class);

    private final AnalyticsPostsAggregator aggregator;

    public AnalyticsPostsScheduler(AnalyticsPostsAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Scheduled(cron = "${posts.analytics.posts.cron:0 */15 * * * *}", zone = "Europe/Moscow")
    @SchedulerLock(name = "analyticsPostsMonthly", lockAtMostFor = "30m", lockAtLeastFor = "30s")
    public void refreshMonthlyAggregates() {
        try {
            var result = aggregator.runIncremental();
            log.debug("analytics posts refresh months={}", result.monthsRecomputed());
        } catch (RuntimeException ex) {
            log.warn("analytics posts refresh failed: {}", ex.toString());
            throw ex;
        }
    }
}
