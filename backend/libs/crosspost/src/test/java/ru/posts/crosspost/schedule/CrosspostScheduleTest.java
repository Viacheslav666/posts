package ru.posts.crosspost.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrosspostScheduleTest {

    @Test
    void disabledScheduleKeepsTheCandidate() {
        Instant candidate = Instant.parse("2026-11-19T02:00:00Z");
        assertThat(CrosspostSchedule.align(Map.of("schedule", Map.of("enabled", false)), candidate))
                .isEqualTo(candidate);
    }

    @Test
    void weekdayHoursShiftIntoTheNextOpenWindowAndHour24IsEndOfDay() {
        // Thursday 2026-11-19 02:00 UTC = 05:00 MSK, before 09:00.
        Instant candidate = Instant.parse("2026-11-19T02:00:00Z");
        Map<String, Object> config = Map.of(
                "schedule",
                Map.of(
                        "enabled",
                        true,
                        "timezone",
                        "Europe/Moscow",
                        "days_of_week",
                        List.of(1, 2, 3, 4, 5),
                        "hours_from",
                        9,
                        "hours_to",
                        18));
        Instant slot = CrosspostSchedule.align(config, candidate);
        assertThat(slot.atZone(ZoneId.of("Europe/Moscow")).getHour()).isEqualTo(9);
        assertThat(slot).isAfter(candidate);

        Instant evening = Instant.parse("2026-11-19T20:00:00Z"); // 23:00 MSK
        Map<String, Object> untilMidnight = Map.of(
                "schedule",
                Map.of(
                        "enabled",
                        true,
                        "timezone",
                        "Europe/Moscow",
                        "days_of_week",
                        List.of(1, 2, 3, 4, 5, 6, 7),
                        "hours_from",
                        0,
                        "hours_to",
                        24));
        assertThat(CrosspostSchedule.align(untilMidnight, evening)).isEqualTo(evening);
    }
}
