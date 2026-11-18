package ru.posts.notifications.pipeline;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import ru.posts.persistence.entity.NotificationSettingsEntity;

/** Evaluates quiet hours and returns when delivery may proceed. */
public final class QuietHoursEvaluator {

    private QuietHoursEvaluator() {}

    /**
     * Returns {@code null} when delivery may proceed now; otherwise the Instant when quiet hours end
     * in the user's timezone.
     */
    public static Instant delayUntil(
            NotificationSettingsEntity settings, String eventType, String channel, Instant now) {
        if (settings == null || !settings.isQuietHoursEnabled()) {
            return null;
        }
        if (NotificationChannels.IN_APP.equals(channel) && NotificationChannels.isCritical(eventType)) {
            return null;
        }
        LocalTime start = settings.getQuietHoursStart();
        LocalTime end = settings.getQuietHoursEnd();
        if (start == null || end == null) {
            return null;
        }
        ZoneId zone = zoneOf(settings.getTimezone());
        ZonedDateTime zoned = now.atZone(zone);
        LocalTime local = zoned.toLocalTime();
        if (!isWithinQuietHours(local, start, end)) {
            return null;
        }
        return endOfQuietHours(zoned, end).toInstant();
    }

    public static boolean isWithinQuietHours(LocalTime now, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            // e.g. 09:00–17:00
            return !now.isBefore(start) && now.isBefore(end);
        }
        // overnight window e.g. 22:00–07:00
        return !now.isBefore(start) || now.isBefore(end);
    }

    static ZonedDateTime endOfQuietHours(ZonedDateTime now, LocalTime end) {
        ZonedDateTime candidate = now.with(end);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("Europe/Moscow");
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            return ZoneId.of("Europe/Moscow");
        }
    }
}
