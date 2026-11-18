package ru.posts.grabber.schedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import ru.posts.grabber.config.GrabberConfigSupport;

/**
 * Working-hours gate for grabber rules (timezone + days + hour range).
 *
 * <p>When {@code schedule.enabled=false} (default) the window is always open.
 */
public final class WorkingWindow {

    private WorkingWindow() {}

    public static boolean isOpen(Map<String, Object> config, Instant now) {
        Map<String, Object> schedule = GrabberConfigSupport.schedule(config);
        if (!bool(schedule.get("enabled"), false)) {
            return true;
        }
        ZoneId zone = zoneId(string(schedule.get("timezone"), "Europe/Moscow"));
        ZonedDateTime zdt = (now == null ? Instant.now() : now).atZone(zone);
        Set<Integer> days = dayNumbers(schedule.get("days_of_week"));
        // ISO day: Monday=1 … Sunday=7
        int isoDay = zdt.getDayOfWeek().getValue();
        if (!days.isEmpty() && !days.contains(isoDay)) {
            return false;
        }
        int fromHour = intVal(schedule.get("hours_from"), 0);
        int toHour = intVal(schedule.get("hours_to"), 24);
        fromHour = clamp(fromHour, 0, 23);
        toHour = clamp(toHour, 0, 24);
        if (fromHour == toHour) {
            return true; // full day
        }
        LocalTime time = zdt.toLocalTime();
        LocalTime from = LocalTime.of(fromHour, 0);
        if (toHour >= 24) {
            return !time.isBefore(from);
        }
        LocalTime to = LocalTime.of(toHour, 0);
        if (from.isBefore(to) || from.equals(to)) {
            return !time.isBefore(from) && time.isBefore(to);
        }
        // overnight window e.g. 22→6
        return !time.isBefore(from) || time.isBefore(to);
    }

    public static String skipReasonIfClosed(Map<String, Object> config, Instant now) {
        return isOpen(config, now) ? null : "outside_working_window";
    }

    private static ZoneId zoneId(String raw) {
        try {
            return ZoneId.of(raw);
        } catch (Exception ex) {
            return ZoneId.of("Europe/Moscow");
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<Integer> dayNumbers(Object raw) {
        Set<Integer> out = new LinkedHashSet<>();
        if (!(raw instanceof Collection<?> col) || col.isEmpty()) {
            // default all days
            for (DayOfWeek d : DayOfWeek.values()) {
                out.add(d.getValue());
            }
            return out;
        }
        for (Object item : col) {
            if (item instanceof Number n) {
                int v = n.intValue();
                if (v >= 1 && v <= 7) {
                    out.add(v);
                }
            } else if (item != null) {
                String s = item.toString().trim().toLowerCase(Locale.ROOT);
                Integer mapped =
                        switch (s) {
                            case "mon", "monday", "пн" -> 1;
                            case "tue", "tuesday", "вт" -> 2;
                            case "wed", "wednesday", "ср" -> 3;
                            case "thu", "thursday", "чт" -> 4;
                            case "fri", "friday", "пт" -> 5;
                            case "sat", "saturday", "сб" -> 6;
                            case "sun", "sunday", "вс" -> 7;
                            default -> {
                                try {
                                    int v = Integer.parseInt(s);
                                    yield (v >= 1 && v <= 7) ? v : null;
                                } catch (NumberFormatException ex) {
                                    yield null;
                                }
                            }
                        };
                if (mapped != null) {
                    out.add(mapped);
                }
            }
        }
        if (out.isEmpty()) {
            return Set.copyOf(List.of(1, 2, 3, 4, 5, 6, 7));
        }
        return out;
    }

    private static boolean bool(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static int intVal(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return defaultValue;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String string(Object raw, String defaultValue) {
        return raw == null || raw.toString().isBlank() ? defaultValue : raw.toString();
    }
}
