package ru.posts.crosspost.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ru.posts.crosspost.config.CrosspostConfigSupport;

/**
 * Shifts a crosspost run into the next open schedule window (FE-24: enabled, timezone, ISO weekdays,
 * hours_from inclusive, hours_to exclusive).
 */
public final class CrosspostSchedule {

    private CrosspostSchedule() {}

    public static Instant align(Map<String, Object> config, Instant candidate) {
        Instant base = candidate == null ? Instant.now() : candidate;
        Map<String, Object> schedule = CrosspostConfigSupport.map(
                CrosspostConfigSupport.withDefaults(config).get("schedule"));
        if (!CrosspostConfigSupport.bool(schedule.get("enabled"), false)) {
            return base;
        }
        ZoneId zone = zone(text(schedule.get("timezone"), "Europe/Moscow"));
        Set<Integer> days = days(schedule.get("days_of_week"));
        int hoursFrom = hour(schedule.get("hours_from"), 0);
        int hoursTo = hour(schedule.get("hours_to"), 24);
        if (hoursFrom < 0) {
            hoursFrom = 0;
        }
        if (hoursTo > 24) {
            hoursTo = 24;
        }
        if (hoursFrom >= hoursTo && hoursTo != 24) {
            return base;
        }
        ZonedDateTime cursor = base.atZone(zone);
        for (int dayOffset = 0; dayOffset < 14; dayOffset++) {
            LocalDate date = cursor.toLocalDate().plusDays(dayOffset);
            if (!days.contains(date.getDayOfWeek().getValue())) {
                continue;
            }
            ZonedDateTime open = ZonedDateTime.of(date, LocalTime.of(Math.min(hoursFrom, 23), 0), zone);
            if (hoursFrom >= 24) {
                open = date.plusDays(1).atStartOfDay(zone);
            }
            ZonedDateTime close = hoursTo >= 24
                    ? date.plusDays(1).atStartOfDay(zone)
                    : ZonedDateTime.of(date, LocalTime.of(hoursTo, 0), zone);
            ZonedDateTime slot = dayOffset == 0 ? cursor : open;
            if (slot.isBefore(open)) {
                slot = open;
            }
            if (slot.isBefore(close)) {
                return slot.toInstant();
            }
        }
        return base;
    }

    private static ZoneId zone(String raw) {
        try {
            return ZoneId.of(raw);
        } catch (Exception ex) {
            return ZoneId.of("Europe/Moscow");
        }
    }

    private static String text(Object raw, String def) {
        if (raw instanceof String s && !s.isBlank()) {
            return s;
        }
        return def;
    }

    private static int hour(Object raw, int def) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        return def;
    }

    private static Set<Integer> days(Object raw) {
        Set<Integer> out = new LinkedHashSet<>();
        if (raw instanceof Collection<?> col) {
            for (Object item : col) {
                if (item instanceof Number n) {
                    int v = n.intValue();
                    if (v >= 1 && v <= 7) {
                        out.add(v);
                    }
                }
            }
        }
        if (out.isEmpty()) {
            return Set.copyOf(List.of(1, 2, 3, 4, 5, 6, 7));
        }
        return out;
    }
}
