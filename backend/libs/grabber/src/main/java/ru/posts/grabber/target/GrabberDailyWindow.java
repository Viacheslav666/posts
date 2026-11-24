package ru.posts.grabber.target;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import ru.posts.grabber.config.GrabberConfigSupport;

/**
 * Calendar-day window for grabber publish caps. The day is the schedule timezone
 * ({@code Europe/Moscow} when the rule has none), not a rolling 24 hours.
 */
public final class GrabberDailyWindow {

    /** Statuses that consume the daily publish cap. Pending moderation does not. */
    public static final List<String> QUOTA_STATUSES = List.of("published", "scheduled");

    public static final String RATE_LIMIT_PER_DAY = "rate_limit_per_day";
    public static final String RATE_LIMIT_PER_SOURCE = "rate_limit_per_source_per_day";

    private GrabberDailyWindow() {}

    public static Instant start(Map<String, Object> config, Instant now) {
        Instant clock = now == null ? Instant.now() : now;
        ZoneId zone = zone(config);
        return clock.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
    }

    public static ZoneId zone(Map<String, Object> config) {
        try {
            return ZoneId.of(GrabberConfigSupport.scheduleTimezone(config));
        } catch (DateTimeException ex) {
            return ZoneId.of("Europe/Moscow");
        }
    }

    /** Rate-limit skips are not terminal: the same post is copied after the calendar day resets. */
    public static boolean isReopenableRateLimit(String status, String reason) {
        return "filtered_out".equals(status)
                && (RATE_LIMIT_PER_DAY.equals(reason) || RATE_LIMIT_PER_SOURCE.equals(reason));
    }
}
