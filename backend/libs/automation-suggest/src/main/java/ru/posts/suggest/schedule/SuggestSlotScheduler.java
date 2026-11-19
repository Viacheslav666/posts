package ru.posts.suggest.schedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import ru.posts.suggest.config.SuggestConfigSupport;

/**
 * Computes the next publish slot for a suggested post given schedule windows, min interval, and
 * ads pause.
 */
@Component
public class SuggestSlotScheduler {

    public Instant nextSlot(Map<String, Object> config, Instant now, Instant lastPublishAt, boolean lastWallWasAds) {
        Instant base = now == null ? Instant.now() : now;
        Map<String, Object> schedule = SuggestConfigSupport.schedule(config);
        int minInterval = SuggestConfigSupport.intVal(schedule.get("min_interval_minutes"), 0);
        if (minInterval > 0 && lastPublishAt != null) {
            Instant earliest = lastPublishAt.plusSeconds(minInterval * 60L);
            if (earliest.isAfter(base)) {
                base = earliest;
            }
        }
        Map<String, Object> adsPause = SuggestConfigSupport.adsPause(config);
        int pauseAfterAds = SuggestConfigSupport.intVal(schedule.get("pause_after_ads_minutes"), 0);
        if (pauseAfterAds <= 0) {
            pauseAfterAds = SuggestConfigSupport.intVal(adsPause.get("pause_after_ads_minutes"), 0);
        }
        if (SuggestConfigSupport.bool(adsPause.get("enabled"), true)
                && lastWallWasAds
                && pauseAfterAds > 0
                && lastPublishAt != null) {
            Instant earliest = lastPublishAt.plusSeconds(pauseAfterAds * 60L);
            if (earliest.isAfter(base)) {
                base = earliest;
            }
        }

        if (!SuggestConfigSupport.bool(schedule.get("enabled"), false)) {
            return base;
        }

        ZoneId zone = zoneId(SuggestConfigSupport.string(schedule.get("timezone"), "Europe/Moscow"));
        List<Window> windows = parseWindows(schedule.get("windows"));
        if (windows.isEmpty()) {
            return base;
        }

        ZonedDateTime cursor = base.atZone(zone);
        // Search up to 14 days ahead for an open window.
        for (int dayOffset = 0; dayOffset < 14; dayOffset++) {
            LocalDate date = cursor.toLocalDate().plusDays(dayOffset);
            int isoDay = date.getDayOfWeek().getValue();
            for (Window w : windows) {
                if (!w.days().contains(isoDay)) {
                    continue;
                }
                ZonedDateTime from = ZonedDateTime.of(date, w.from(), zone);
                ZonedDateTime to = ZonedDateTime.of(date, w.to(), zone);
                if (!w.from().isBefore(w.to())) {
                    // overnight: treat as from → 24:00 of this day
                    to = ZonedDateTime.of(date.plusDays(1), w.to(), zone);
                }
                ZonedDateTime candidate = dayOffset == 0 ? cursor : from;
                if (candidate.isBefore(from)) {
                    candidate = from;
                }
                if (!candidate.isBefore(to)) {
                    continue;
                }
                return candidate.toInstant();
            }
        }
        return base;
    }

    private static ZoneId zoneId(String raw) {
        try {
            return ZoneId.of(raw);
        } catch (Exception ex) {
            return ZoneId.of("Europe/Moscow");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Window> parseWindows(Object raw) {
        List<Window> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            m.forEach((k, v) -> map.put(String.valueOf(k), v));
            Set<Integer> days = dayNumbers(map.get("days_of_week"));
            LocalTime from = parseTime(SuggestConfigSupport.string(map.get("from"), "00:00"));
            LocalTime to = parseTime(SuggestConfigSupport.string(map.get("to"), "23:59"));
            out.add(new Window(days, from, to));
        }
        return out;
    }

    private static LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw);
        } catch (Exception ex) {
            return LocalTime.MIDNIGHT;
        }
    }

    private static Set<Integer> dayNumbers(Object raw) {
        Set<Integer> out = new LinkedHashSet<>();
        if (!(raw instanceof Collection<?> col) || col.isEmpty()) {
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

    private record Window(Set<Integer> days, LocalTime from, LocalTime to) {}
}
