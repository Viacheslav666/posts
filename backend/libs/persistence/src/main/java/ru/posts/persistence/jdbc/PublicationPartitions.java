package ru.posts.persistence.jdbc;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Creates monthly range partitions for {@code publications} and {@code social_remote_actions}.
 * The initial migrations only cover through 2027-09; the scheduler extends the horizon.
 */
public final class PublicationPartitions {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy_MM");
    private static final Set<String> TABLES = Set.of("publications", "social_remote_actions");

    private PublicationPartitions() {}

    public static void ensureMonth(JdbcTemplate jdbc, String table, Instant instant) {
        if (!TABLES.contains(table)) {
            throw new IllegalArgumentException("Unsupported partitioned table: " + table);
        }
        YearMonth ym = YearMonth.from(instant.atZone(ZoneOffset.UTC));
        String name = table + "_" + MONTH.format(ym);
        if (!name.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Unsafe partition name: " + name);
        }
        YearMonth next = ym.plusMonths(1);
        String from = ym.atDay(1) + "+00";
        String to = next.atDay(1) + "+00";
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + name + " PARTITION OF " + table + " FOR VALUES FROM ('" + from
                + "') TO ('" + to + "')");
    }

    /** Months the rolling job is expected to have created (today through +18). */
    public static boolean withinRollingHorizon(Instant instant) {
        YearMonth ym = YearMonth.from(instant.atZone(ZoneOffset.UTC));
        YearMonth min = YearMonth.of(2026, 10);
        YearMonth max = YearMonth.now(ZoneOffset.UTC).plusMonths(18);
        return !ym.isBefore(min) && !ym.isAfter(max);
    }
}
