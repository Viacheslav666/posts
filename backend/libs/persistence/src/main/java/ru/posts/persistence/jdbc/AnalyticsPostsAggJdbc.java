package ru.posts.persistence.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Incremental, idempotent monthly analytics materialisation (BE-30).
 *
 * <p>Reads {@code publications} only within Moscow month bounds (partition pruning) and ORD
 * invoices for the period; upserts {@code analytics_posts_monthly}. Never used by the hot
 * publish path.
 */
public class AnalyticsPostsAggJdbc {

    public static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    public static final int TOP_ERRORS_LIMIT = 10;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal FEE_RATE = new BigDecimal("0.03");
    private static final TypeReference<Map<String, Long>> LONG_MAP = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> ERROR_LIST = new TypeReference<>() {};

    public record MonthKey(UUID workspaceId, int year, int month) {}

    public record MonthAggregate(
            UUID workspaceId,
            int year,
            int month,
            long regularCount,
            long adCount,
            long repostCount,
            Map<String, Long> networkSucceeded,
            Map<String, Long> networkFailed,
            List<Map<String, Object>> topErrors,
            BigDecimal revenueWithVat,
            BigDecimal revenueWithoutVat,
            BigDecimal revenueVat,
            BigDecimal revenueFee3Pct,
            Instant computedAt) {}

    public record JobRunResult(int monthsRecomputed, Instant newWatermark) {}

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public AnalyticsPostsAggJdbc(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbc = jdbcClient;
        this.objectMapper = objectMapper;
    }

    /** Full recompute of every month in {@code year} for one workspace (tests / backfill). */
    @Transactional
    public int recomputeWorkspaceYear(UUID workspaceId, int year) {
        int count = 0;
        for (int month = 1; month <= 12; month++) {
            upsertMonth(recomputeMonth(workspaceId, year, month));
            count++;
        }
        return count;
    }

    /** Recompute a single month and upsert (idempotent). */
    @Transactional
    public MonthAggregate recomputeAndUpsert(UUID workspaceId, int year, int month) {
        MonthAggregate aggregate = recomputeMonth(workspaceId, year, month);
        upsertMonth(aggregate);
        return aggregate;
    }

    /**
     * Incremental job step: find dirty months since the stored watermark, recompute each
     * (idempotent upsert), then advance the watermark. Empty dirty sets only bump the watermark.
     */
    @Transactional
    public JobRunResult runIncremental(Instant now) {
        Instant watermark = loadWatermark();
        List<MonthKey> dirty = findDirtyMonths(watermark, now);
        int recomputed = 0;
        for (MonthKey key : dirty) {
            upsertMonth(recomputeMonth(key.workspaceId(), key.year(), key.month()));
            recomputed++;
        }
        saveWatermark(now, recomputed);
        return new JobRunResult(recomputed, now);
    }

    public Instant loadWatermark() {
        return jdbc.sql("SELECT last_watermark FROM analytics_posts_job_state WHERE id = 1")
                .query((rs, rowNum) -> rs.getTimestamp("last_watermark").toInstant())
                .optional()
                .orElse(Instant.EPOCH);
    }

    public List<MonthAggregate> loadYear(UUID workspaceId, int year) {
        return jdbc.sql(
                        """
                        SELECT workspace_id, year, month, regular_count, ad_count, repost_count,
                               network_succeeded::text AS network_succeeded,
                               network_failed::text AS network_failed,
                               top_errors::text AS top_errors,
                               revenue_with_vat, revenue_without_vat, revenue_vat, revenue_fee_3pct,
                               computed_at
                        FROM analytics_posts_monthly
                        WHERE workspace_id = :workspaceId AND year = :year
                        ORDER BY month
                        """)
                .param("workspaceId", workspaceId)
                .param("year", year)
                .query((rs, rowNum) -> new MonthAggregate(
                        (UUID) rs.getObject("workspace_id"),
                        rs.getInt("year"),
                        rs.getInt("month"),
                        rs.getLong("regular_count"),
                        rs.getLong("ad_count"),
                        rs.getLong("repost_count"),
                        readLongMap(rs.getString("network_succeeded")),
                        readLongMap(rs.getString("network_failed")),
                        readErrorList(rs.getString("top_errors")),
                        rs.getBigDecimal("revenue_with_vat"),
                        rs.getBigDecimal("revenue_without_vat"),
                        rs.getBigDecimal("revenue_vat"),
                        rs.getBigDecimal("revenue_fee_3pct"),
                        rs.getTimestamp("computed_at").toInstant()))
                .list();
    }

    public MonthAggregate recomputeMonth(UUID workspaceId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        Instant from = ym.atDay(1).atStartOfDay(MOSCOW).toInstant();
        Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(MOSCOW).toInstant();

        long[] counts = jdbc.sql(
                        """
                        SELECT
                          COUNT(*) FILTER (
                            WHERE NOT COALESCE(po.is_ad, FALSE)
                              AND (po.base_content->'repost') IS NULL
                          ) AS regular_count,
                          COUNT(*) FILTER (WHERE COALESCE(po.is_ad, FALSE)) AS ad_count,
                          COUNT(*) FILTER (
                            WHERE jsonb_exists(po.base_content, 'repost')
                              AND jsonb_typeof(po.base_content->'repost') = 'object'
                          ) AS repost_count
                        FROM publications p
                        JOIN posts po ON po.id = p.post_id
                        WHERE p.workspace_id = :workspaceId
                          AND p.run_at >= :fromTs
                          AND p.run_at < :toTs
                        """)
                .param("workspaceId", workspaceId)
                .param("fromTs", Timestamp.from(from))
                .param("toTs", Timestamp.from(to))
                .query((rs, rowNum) ->
                        new long[] {rs.getLong("regular_count"), rs.getLong("ad_count"), rs.getLong("repost_count")})
                .single();

        Map<String, Long> succeeded = new LinkedHashMap<>();
        Map<String, Long> failed = new LinkedHashMap<>();
        jdbc.sql(
                        """
                        SELECT network,
                               COUNT(*) FILTER (WHERE state = 'succeeded') AS succeeded,
                               COUNT(*) FILTER (
                                 WHERE state IN ('failed_retryable', 'failed_permanent')
                               ) AS failed
                        FROM publications
                        WHERE workspace_id = :workspaceId
                          AND run_at >= :fromTs
                          AND run_at < :toTs
                        GROUP BY network
                        ORDER BY network
                        """)
                .param("workspaceId", workspaceId)
                .param("fromTs", Timestamp.from(from))
                .param("toTs", Timestamp.from(to))
                .query((rs, rowNum) -> {
                    String network = rs.getString("network");
                    long s = rs.getLong("succeeded");
                    long f = rs.getLong("failed");
                    if (s > 0) {
                        succeeded.put(network, s);
                    }
                    if (f > 0) {
                        failed.put(network, f);
                    }
                    return null;
                })
                .list();

        List<Map<String, Object>> topErrors = jdbc.sql(
                        """
                        SELECT error_code,
                               COALESCE(error_message, '') AS error_message,
                               COUNT(*)::bigint AS cnt,
                               (array_agg(id ORDER BY created_at DESC))[1] AS publication_id,
                               (array_agg(post_id ORDER BY created_at DESC))[1] AS post_id,
                               (array_agg(run_at ORDER BY created_at DESC))[1] AS run_at,
                               (array_agg(network ORDER BY created_at DESC))[1] AS network
                        FROM publications
                        WHERE workspace_id = :workspaceId
                          AND run_at >= :fromTs
                          AND run_at < :toTs
                          AND state IN ('failed_retryable', 'failed_permanent')
                          AND error_code IS NOT NULL
                        GROUP BY error_code, COALESCE(error_message, '')
                        ORDER BY cnt DESC, error_code
                        LIMIT :limit
                        """)
                .param("workspaceId", workspaceId)
                .param("fromTs", Timestamp.from(from))
                .param("toTs", Timestamp.from(to))
                .param("limit", TOP_ERRORS_LIMIT)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("errorCode", rs.getString("error_code"));
                    row.put("errorMessage", rs.getString("error_message"));
                    row.put("count", rs.getLong("cnt"));
                    UUID publicationId = (UUID) rs.getObject("publication_id");
                    row.put("publicationId", publicationId == null ? null : publicationId.toString());
                    UUID postId = (UUID) rs.getObject("post_id");
                    row.put("postId", postId == null ? null : postId.toString());
                    Timestamp runAt = rs.getTimestamp("run_at");
                    row.put("runAt", runAt == null ? null : runAt.toInstant().toString());
                    row.put("network", rs.getString("network"));
                    if (publicationId != null) {
                        row.put("retryPath", "/api/v1/publications/" + publicationId + "/retry");
                    }
                    return row;
                })
                .list();

        BigDecimal withVat = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal withoutVat = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal vat = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        List<Map<String, Object>> invoiceRows = jdbc.sql(
                        """
                        SELECT i.amount, COALESCE(c.vat_rate, 'without_vat') AS vat_rate
                        FROM ord_invoices i
                        LEFT JOIN ord_contracts c ON c.id = i.contract_id
                        WHERE i.workspace_id = :workspaceId
                          AND i.period_year = :year
                          AND i.period_month = :month
                          AND i.deleted_at IS NULL
                          AND i.amount IS NOT NULL
                        """)
                .param("workspaceId", workspaceId)
                .param("year", year)
                .param("month", month)
                .query((rs, rowNum) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("amount", rs.getBigDecimal("amount"));
                    m.put("vatRate", rs.getString("vat_rate"));
                    return m;
                })
                .list();
        for (Map<String, Object> row : invoiceRows) {
            BigDecimal amount = (BigDecimal) row.get("amount");
            String vatRate = (String) row.get("vatRate");
            MoneySplit split = splitAmountWithVat(amount, vatRate);
            withVat = withVat.add(split.withVat());
            withoutVat = withoutVat.add(split.withoutVat());
            vat = vat.add(split.vat());
        }
        withVat = withVat.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        withoutVat = withoutVat.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        vat = vat.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal fee = withVat.multiply(FEE_RATE).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return new MonthAggregate(
                workspaceId,
                year,
                month,
                counts[0],
                counts[1],
                counts[2],
                Map.copyOf(succeeded),
                Map.copyOf(failed),
                List.copyOf(topErrors),
                withVat,
                withoutVat,
                vat,
                fee,
                Instant.now());
    }

    public void upsertMonth(MonthAggregate aggregate) {
        jdbc.sql(
                        """
                        INSERT INTO analytics_posts_monthly (
                          workspace_id, year, month,
                          regular_count, ad_count, repost_count,
                          network_succeeded, network_failed, top_errors,
                          revenue_with_vat, revenue_without_vat, revenue_vat, revenue_fee_3pct,
                          computed_at
                        ) VALUES (
                          :workspaceId, :year, :month,
                          :regularCount, :adCount, :repostCount,
                          CAST(:networkSucceeded AS jsonb), CAST(:networkFailed AS jsonb), CAST(:topErrors AS jsonb),
                          :revenueWithVat, :revenueWithoutVat, :revenueVat, :revenueFee3Pct,
                          :computedAt
                        )
                        ON CONFLICT (workspace_id, year, month) DO UPDATE SET
                          regular_count = EXCLUDED.regular_count,
                          ad_count = EXCLUDED.ad_count,
                          repost_count = EXCLUDED.repost_count,
                          network_succeeded = EXCLUDED.network_succeeded,
                          network_failed = EXCLUDED.network_failed,
                          top_errors = EXCLUDED.top_errors,
                          revenue_with_vat = EXCLUDED.revenue_with_vat,
                          revenue_without_vat = EXCLUDED.revenue_without_vat,
                          revenue_vat = EXCLUDED.revenue_vat,
                          revenue_fee_3pct = EXCLUDED.revenue_fee_3pct,
                          computed_at = EXCLUDED.computed_at
                        """)
                .param("workspaceId", aggregate.workspaceId())
                .param("year", aggregate.year())
                .param("month", aggregate.month())
                .param("regularCount", aggregate.regularCount())
                .param("adCount", aggregate.adCount())
                .param("repostCount", aggregate.repostCount())
                .param("networkSucceeded", writeJson(aggregate.networkSucceeded()))
                .param("networkFailed", writeJson(aggregate.networkFailed()))
                .param("topErrors", writeJson(aggregate.topErrors()))
                .param("revenueWithVat", aggregate.revenueWithVat())
                .param("revenueWithoutVat", aggregate.revenueWithoutVat())
                .param("revenueVat", aggregate.revenueVat())
                .param("revenueFee3Pct", aggregate.revenueFee3Pct())
                .param("computedAt", Timestamp.from(aggregate.computedAt()))
                .update();
    }

    private List<MonthKey> findDirtyMonths(Instant watermark, Instant now) {
        // created_at (not run_at): scheduled future publications still dirty their Moscow month on insert.
        List<MonthKey> fromPubs = jdbc.sql(
                        """
                        SELECT DISTINCT workspace_id,
                               EXTRACT(YEAR FROM run_at AT TIME ZONE 'Europe/Moscow')::int AS y,
                               EXTRACT(MONTH FROM run_at AT TIME ZONE 'Europe/Moscow')::int AS m
                        FROM publications
                        WHERE created_at >= :watermark AND created_at < :nowTs
                        """)
                .param("watermark", Timestamp.from(watermark))
                .param("nowTs", Timestamp.from(now))
                .query((rs, rowNum) ->
                        new MonthKey((UUID) rs.getObject("workspace_id"), rs.getInt("y"), rs.getInt("m")))
                .list();

        List<MonthKey> fromInvoices = jdbc.sql(
                        """
                        SELECT DISTINCT workspace_id, period_year AS y, period_month AS m
                        FROM ord_invoices
                        WHERE updated_at >= :watermark AND updated_at < :nowTs
                        """)
                .param("watermark", Timestamp.from(watermark))
                .param("nowTs", Timestamp.from(now))
                .query((rs, rowNum) ->
                        new MonthKey((UUID) rs.getObject("workspace_id"), rs.getInt("y"), rs.getInt("m")))
                .list();

        return mergeUnique(fromPubs, fromInvoices);
    }

    private void saveWatermark(Instant watermark, int monthsRecomputed) {
        jdbc.sql(
                        """
                        UPDATE analytics_posts_job_state
                        SET last_watermark = :watermark,
                            last_success_at = :successAt,
                            last_months_recomputed = :months
                        WHERE id = 1
                        """)
                .param("watermark", Timestamp.from(watermark))
                .param("successAt", Timestamp.from(Instant.now()))
                .param("months", monthsRecomputed)
                .update();
    }

    private static List<MonthKey> mergeUnique(List<MonthKey> a, List<MonthKey> b) {
        Map<String, MonthKey> map = new LinkedHashMap<>();
        for (MonthKey key : a) {
            map.put(key.workspaceId() + ":" + key.year() + ":" + key.month(), key);
        }
        for (MonthKey key : b) {
            map.put(key.workspaceId() + ":" + key.year() + ":" + key.month(), key);
        }
        return new ArrayList<>(map.values());
    }

    private Map<String, Long> readLongMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Long> map = objectMapper.readValue(json, LONG_MAP);
            return map == null ? Map.of() : Map.copyOf(map);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid network stats jsonb", ex);
        }
    }

    private List<Map<String, Object>> readErrorList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json, ERROR_LIST);
            return list == null ? List.of() : List.copyOf(list);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid top_errors jsonb", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNullElse(value, Map.of()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialise analytics jsonb", ex);
        }
    }

    /**
     * Mirrors {@code VatCalculator} (ORD amounts include VAT when a taxable rate is set). Kept local
     * so persistence does not depend on {@code libs:ord-api}.
     */
    public static MoneySplit splitAmountWithVat(BigDecimal amountWithVat, String vatRateWire) {
        BigDecimal gross = amountWithVat == null
                ? BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : amountWithVat.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal percent = parseVatPercent(vatRateWire);
        if (percent == null || percent.signum() == 0) {
            return new MoneySplit(gross, gross, BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        }
        BigDecimal hundred = new BigDecimal("100");
        BigDecimal net = gross.multiply(hundred).divide(hundred.add(percent), MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal vat = gross.subtract(net).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new MoneySplit(gross, net, vat);
    }

    private static BigDecimal parseVatPercent(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "without_vat", "without", "none" -> null;
            case "0", "0%" -> BigDecimal.ZERO;
            case "5", "5%" -> new BigDecimal("5");
            case "7", "7%" -> new BigDecimal("7");
            case "10", "10%" -> new BigDecimal("10");
            case "20", "20%" -> new BigDecimal("20");
            case "22", "22%" -> new BigDecimal("22");
            default -> null;
        };
    }

    public record MoneySplit(BigDecimal withVat, BigDecimal withoutVat, BigDecimal vat) {}
}
