package ru.posts.analytics;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.persistence.jdbc.AnalyticsPostsAggJdbc;
import ru.posts.persistence.jdbc.AnalyticsPostsAggJdbc.JobRunResult;
import ru.posts.persistence.jdbc.AnalyticsPostsAggJdbc.MonthAggregate;

/**
 * Orchestrates incremental / on-demand materialisation of {@code analytics_posts_monthly} (BE-30).
 * Distributed locking is applied by the scheduler ShedLock wrapper.
 */
@Profile("!openapi")
@Service
public class AnalyticsPostsAggregator {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsPostsAggregator.class);

    private final AnalyticsPostsAggJdbc jdbc;
    private final AnalyticsPostsMetrics metrics;

    public AnalyticsPostsAggregator(AnalyticsPostsAggJdbc jdbc, AnalyticsPostsMetrics metrics) {
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    /** Incremental pass used by the ShedLock job. Idempotent upserts of dirty months. */
    @Transactional
    public JobRunResult runIncremental() {
        Instant started = Instant.now();
        try {
            JobRunResult result = jdbc.runIncremental(started);
            metrics.recordSuccess(result.monthsRecomputed(), Duration.between(started, Instant.now()));
            log.info(
                    "analytics posts job done months={} watermark={}",
                    result.monthsRecomputed(),
                    result.newWatermark());
            return result;
        } catch (RuntimeException ex) {
            metrics.recordFailure();
            throw ex;
        }
    }

    /** Recompute all 12 months for a workspace/year (IT / backfill). */
    @Transactional
    public int recomputeWorkspaceYear(UUID workspaceId, int year) {
        return jdbc.recomputeWorkspaceYear(workspaceId, year);
    }

    @Transactional
    public MonthAggregate recomputeMonth(UUID workspaceId, int year, int month) {
        return jdbc.recomputeAndUpsert(workspaceId, year, month);
    }

    @Transactional(readOnly = true)
    public List<MonthAggregate> loadYear(UUID workspaceId, int year) {
        return jdbc.loadYear(workspaceId, year);
    }
}
