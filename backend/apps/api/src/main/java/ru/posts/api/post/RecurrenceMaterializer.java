package ru.posts.api.post;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.post.PostDtos;

/**
 * Expands recurrence into concrete {@code run_at} instants for a 14-day publication horizon
 * (02-backend.md §5.1).
 */
public final class RecurrenceMaterializer {

    public static final int HORIZON_DAYS = 14;

    private RecurrenceMaterializer() {}

    public static List<Instant> materialize(
            Instant scheduledAt, PostDtos.Recurrence recurrence, String timezone, Instant now) {
        if (scheduledAt == null) {
            return List.of();
        }
        ZoneId zone = resolveZone(timezone);
        Instant horizonEnd = now.plus(HORIZON_DAYS, ChronoUnit.DAYS);
        Instant until = recurrence != null && recurrence.until() != null ? recurrence.until() : horizonEnd;
        Instant hardEnd = until.isBefore(horizonEnd) ? until : horizonEnd;

        ApiEnums.RecurrenceFreq freq =
                recurrence == null || recurrence.freq() == null ? ApiEnums.RecurrenceFreq.once : recurrence.freq();
        if (freq == ApiEnums.RecurrenceFreq.once) {
            return scheduledAt.isAfter(hardEnd) ? List.of() : List.of(scheduledAt);
        }

        int interval = recurrence.interval() == null || recurrence.interval() < 1 ? 1 : recurrence.interval();
        Set<java.time.DayOfWeek> weekdays = parseWeekdays(freq, recurrence.byWeekday());

        List<Instant> result = new ArrayList<>();
        ZonedDateTime cursor = scheduledAt.atZone(zone);
        ZonedDateTime end = hardEnd.atZone(zone);
        int guard = 0;
        while (!cursor.toInstant().isAfter(hardEnd) && guard++ < 500) {
            if (matches(cursor, freq, weekdays) && !cursor.toInstant().isAfter(hardEnd)) {
                Instant runAt = cursor.toInstant();
                if (!runAt.isAfter(hardEnd)) {
                    result.add(runAt);
                }
            }
            cursor = advance(cursor, freq, interval);
            if (cursor.isAfter(end.plusDays(1))) {
                break;
            }
        }
        if (result.isEmpty() && !scheduledAt.isAfter(hardEnd)) {
            result.add(scheduledAt);
        }
        return List.copyOf(result);
    }

    private static boolean matches(
            ZonedDateTime cursor, ApiEnums.RecurrenceFreq freq, Set<java.time.DayOfWeek> weekdays) {
        return switch (freq) {
            case once, daily, every_n_days, weekly, every_n_weeks, monthly, every_n_months -> true;
            case weekdays, weekends -> weekdays.contains(cursor.getDayOfWeek());
        };
    }

    private static ZonedDateTime advance(ZonedDateTime cursor, ApiEnums.RecurrenceFreq freq, int interval) {
        return switch (freq) {
            case once -> cursor.plusYears(100);
            case daily, every_n_days -> cursor.plusDays(interval);
            case weekdays, weekends -> cursor.plusDays(1);
            case weekly, every_n_weeks -> cursor.plusWeeks(interval);
            case monthly, every_n_months -> {
                LocalDate next = cursor.toLocalDate().plusMonths(interval);
                int day = Math.min(cursor.getDayOfMonth(), next.lengthOfMonth());
                yield cursor.withDayOfMonth(1).plusMonths(interval).withDayOfMonth(day);
            }
        };
    }

    private static Set<java.time.DayOfWeek> parseWeekdays(ApiEnums.RecurrenceFreq freq, List<String> byWeekday) {
        if (freq == ApiEnums.RecurrenceFreq.weekdays) {
            return EnumSet.of(
                    java.time.DayOfWeek.MONDAY,
                    java.time.DayOfWeek.TUESDAY,
                    java.time.DayOfWeek.WEDNESDAY,
                    java.time.DayOfWeek.THURSDAY,
                    java.time.DayOfWeek.FRIDAY);
        }
        if (freq == ApiEnums.RecurrenceFreq.weekends) {
            return EnumSet.of(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY);
        }
        if (byWeekday == null || byWeekday.isEmpty()) {
            return EnumSet.allOf(java.time.DayOfWeek.class);
        }
        EnumSet<java.time.DayOfWeek> set = EnumSet.noneOf(java.time.DayOfWeek.class);
        for (String raw : byWeekday) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String v = raw.trim().toUpperCase(Locale.ROOT);
            set.add(
                    switch (v) {
                        case "MO", "MON", "MONDAY" -> java.time.DayOfWeek.MONDAY;
                        case "TU", "TUE", "TUESDAY" -> java.time.DayOfWeek.TUESDAY;
                        case "WE", "WED", "WEDNESDAY" -> java.time.DayOfWeek.WEDNESDAY;
                        case "TH", "THU", "THURSDAY" -> java.time.DayOfWeek.THURSDAY;
                        case "FR", "FRI", "FRIDAY" -> java.time.DayOfWeek.FRIDAY;
                        case "SA", "SAT", "SATURDAY" -> java.time.DayOfWeek.SATURDAY;
                        case "SU", "SUN", "SUNDAY" -> java.time.DayOfWeek.SUNDAY;
                        default -> java.time.DayOfWeek.MONDAY;
                    });
        }
        return set;
    }

    private static ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("Europe/Moscow");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception ex) {
            return ZoneId.of("Europe/Moscow");
        }
    }
}
