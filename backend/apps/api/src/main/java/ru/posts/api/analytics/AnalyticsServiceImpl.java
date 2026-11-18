package ru.posts.api.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.analytics.AnalyticsPostsAggregator;
import ru.posts.api.web.dto.analytics.AnalyticsDtos;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.persistence.jdbc.AnalyticsPostsAggJdbc;
import ru.posts.persistence.jdbc.AnalyticsPostsAggJdbc.MonthAggregate;

@Service
@Profile("!openapi")
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final WorkspaceAccessService access;
    private final AnalyticsPostsAggregator aggregator;

    public AnalyticsServiceImpl(WorkspaceAccessService access, AnalyticsPostsAggregator aggregator) {
        this.access = access;
        this.aggregator = aggregator;
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsDtos.PostsAnalyticsResponse getPostsAnalytics(UUID userId, UUID workspaceId, int year) {
        access.requirePermission(workspaceId, userId, "analytics.view");
        List<MonthAggregate> stored = aggregator.loadYear(workspaceId, year);
        Map<Integer, MonthAggregate> byMonth = new LinkedHashMap<>();
        Instant latest = null;
        for (MonthAggregate row : stored) {
            byMonth.put(row.month(), row);
            if (latest == null || row.computedAt().isAfter(latest)) {
                latest = row.computedAt();
            }
        }
        List<AnalyticsDtos.PostsAnalyticsMonth> months = new ArrayList<>(12);
        for (int month = 1; month <= 12; month++) {
            months.add(toDto(byMonth.get(month), month));
        }
        return new AnalyticsDtos.PostsAnalyticsResponse(
                workspaceId, year, AnalyticsPostsAggJdbc.MOSCOW.getId(), months, latest);
    }

    private static AnalyticsDtos.PostsAnalyticsMonth toDto(MonthAggregate row, int month) {
        if (row == null) {
            return new AnalyticsDtos.PostsAnalyticsMonth(
                    month, 0, 0, 0, List.of(), List.of(), new AnalyticsDtos.AdRevenue(ZERO, ZERO, ZERO, ZERO), null);
        }
        Set<String> networks = new LinkedHashSet<>();
        networks.addAll(row.networkSucceeded().keySet());
        networks.addAll(row.networkFailed().keySet());
        List<AnalyticsDtos.NetworkPublicationStats> networkStats = networks.stream()
                .sorted()
                .map(n -> new AnalyticsDtos.NetworkPublicationStats(
                        n,
                        row.networkSucceeded().getOrDefault(n, 0L),
                        row.networkFailed().getOrDefault(n, 0L)))
                .toList();
        List<AnalyticsDtos.TopPublicationError> errors =
                row.topErrors().stream().map(AnalyticsServiceImpl::toError).toList();
        return new AnalyticsDtos.PostsAnalyticsMonth(
                row.month(),
                row.regularCount(),
                row.adCount(),
                row.repostCount(),
                networkStats,
                errors,
                new AnalyticsDtos.AdRevenue(
                        scale(row.revenueWithVat()),
                        scale(row.revenueWithoutVat()),
                        scale(row.revenueVat()),
                        scale(row.revenueFee3Pct())),
                row.computedAt());
    }

    private static AnalyticsDtos.TopPublicationError toError(Map<String, Object> raw) {
        return new AnalyticsDtos.TopPublicationError(
                stringVal(raw.get("errorCode")),
                stringVal(raw.get("errorMessage")),
                longVal(raw.get("count")),
                uuidVal(raw.get("publicationId")),
                uuidVal(raw.get("postId")),
                instantVal(raw.get("runAt")),
                stringVal(raw.get("network")),
                stringVal(raw.get("retryPath")));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }

    private static long longVal(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(value.toString());
    }

    private static UUID uuidVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID u) {
            return u;
        }
        String s = value.toString();
        return s.isBlank() ? null : UUID.fromString(s);
    }

    private static Instant instantVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant i) {
            return i;
        }
        return Instant.parse(value.toString());
    }
}
