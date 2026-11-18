package ru.posts.grabber.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.posts.grabber.config.GrabberConfigSupport;

class WorkingWindowTest {

    @Test
    void disabledMeansAlwaysOpen() {
        Map<String, Object> config = GrabberConfigSupport.withDefaults(Map.of());
        assertThat(WorkingWindow.isOpen(config, Instant.parse("2026-11-18T03:00:00Z")))
                .isTrue();
    }

    @Test
    void respectsHoursAndDaysInTimezone() {
        // 2026-11-18 is Wednesday (3). 12:00 UTC = 15:00 Europe/Moscow
        Instant instant = Instant.parse("2026-11-18T12:00:00Z");
        Map<String, Object> open = GrabberConfigSupport.withDefaults(Map.of(
                "schedule",
                Map.of(
                        "enabled",
                        true,
                        "timezone",
                        "Europe/Moscow",
                        "days_of_week",
                        List.of(3),
                        "hours_from",
                        14,
                        "hours_to",
                        18)));
        assertThat(WorkingWindow.isOpen(open, instant)).isTrue();

        Map<String, Object> closed = GrabberConfigSupport.withDefaults(Map.of(
                "schedule",
                Map.of(
                        "enabled",
                        true,
                        "timezone",
                        "Europe/Moscow",
                        "days_of_week",
                        List.of(3),
                        "hours_from",
                        9,
                        "hours_to",
                        12)));
        assertThat(WorkingWindow.isOpen(closed, instant)).isFalse();
        assertThat(WorkingWindow.skipReasonIfClosed(closed, instant)).isEqualTo("outside_working_window");
    }
}
