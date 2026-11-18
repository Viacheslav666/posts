package ru.posts.api.post;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.post.PostDtos;

/**
 * Expands recurrence into concrete {@code run_at} instants for a 14-day publication horizon
 * (02-backend.md §5.1). Supports optional {@code times[]} (HH:mm) for multiple runs per day.
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
        List<LocalTime> times = parseTimes(recurrence == null ? null : recurrence.times());

        if (freq == ApiEnums.RecurrenceFreq.once) {
            if (times.isEmpty()) {
                return scheduledAt.isAfter(hardEnd) ? List.of() : List.of(scheduledAt);
            }
            LocalDate day = scheduledAt.atZone(zone).toLocalDate();
            return expandTimesOnDay(day, times, zone, hardEnd);
        }

        int interval = recurrence.interval() == null || recurrence.interval() < 1 ? 1 : recurrence.interval();
        Set<java.time.DayOfWeek> weekdays = parseWeekdays(freq, recurrence.byWeekday());

        LinkedHashSet<Instant> result = new LinkedHashSet<>();
        ZonedDateTime cursor = scheduledAt.atZone(zone);
        // Walk by calendar day so times[] can emit multiple Instant per day.
        LocalDate dayCursor = cursor.toLocalDate();
        LocalDate endDay = hardEnd.atZone(zone).toLocalDate().plusDays(1);
        int guard = 0;
        while (!dayCursor.isAfter(endDay) && guard++ < 500) {
            ZonedDateTime dayStart = dayCursor.atStartOfDay(zone);
            if (matches(dayStart, freq, weekdays)) {
                if (times.isEmpty()) {
                    // Preserve historical behaviour: keep scheduledAt's time-of-day / advance chain.
                    // For the first matching day equal to scheduled date, use scheduledAt; else scheduled TOD.
                    Instant runAt;
                    if (dayCursor.equals(scheduledAt.atZone(zone).toLocalDate())) {
                        runAt = scheduledAt;
                    } else {
                        LocalTime tod = scheduledAt.atZone(zone).toLocalTime();
                        runAt = dayCursor.atTime(tod).atZone(zone).toInstant();
                    }
                    if (!runAt.isAfter(hardEnd)) {
                        result.add(runAt);
                    }
                } else {
                    result.addAll(expandTimesOnDay(dayCursor, times, zone, hardEnd));
                }
            }
            dayCursor = advanceDay(dayCursor, freq, interval);
        }
        if (result.isEmpty() && !scheduledAt.isAfter(hardEnd)) {
            result.add(scheduledAt);
        }
        return List.copyOf(result);
    }

    private static List<Instant> expandTimesOnDay(LocalDate day, List<LocalTime> times, ZoneId zone, Instant hardEnd) {
        List<Instant> out = new ArrayList<>();
        for (LocalTime time : times) {
            Instant runAt = day.atTime(time).atZone(zone).toInstant();
            if (!runAt.isAfter(hardEnd)) {
                out.add(runAt);
            }
        }
        return out;
    }

    private static LocalDate advanceDay(LocalDate cursor, ApiEnums.RecurrenceFreq freq, int interval) {
        return switch (freq) {
            case once -> cursor.plusYears(100);
            case daily, every_n_days -> cursor.plusDays(interval);
            case weekdays, weekends -> cursor.plusDays(1);
            case weekly, every_n_weeks -> cursor.plusWeeks(interval);
            case monthly, every_n_months -> cursor.plusMonths(interval);
        };
    }

    private static boolean matches(
            ZonedDateTime cursor, ApiEnums.RecurrenceFreq freq, Set<java.time.DayOfWeek> weekdays) {
        return switch (freq) {
            case once, daily, every_n_days, weekly, every_n_weeks, monthly, every_n_months -> true;
            case weekdays, weekends -> weekdays.contains(cursor.getDayOfWeek());
        };
    }

    private static List<LocalTime> parseTimes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<LocalTime> out = new ArrayList<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                out.add(LocalTime.parse(value.trim()));
            } catch (Exception ignored) {
                // Bean Validation rejects bad values on API; ignore malformed stored jsonb.
            }
        }
        return List.copyOf(out);
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
