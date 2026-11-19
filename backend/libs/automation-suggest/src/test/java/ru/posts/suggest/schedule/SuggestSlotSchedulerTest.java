package ru.posts.suggest.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SuggestSlotSchedulerTest {

    @Test
    void overnightWindowStillOpenAfterMidnightIsNotPushedToTonight() {
        SuggestSlotScheduler scheduler = new SuggestSlotScheduler();
        Map<String, Object> config = Map.of(
                "schedule",
                Map.of(
                        "enabled",
                        true,
                        "timezone",
                        "Europe/Moscow",
                        "windows",
                        List.of(Map.of("days_of_week", List.of(1, 2, 3, 4, 5, 6, 7), "from", "22:00", "to", "02:00")),
                        "min_interval_minutes",
                        0,
                        "pause_after_ads_minutes",
                        0));
        // 2026-11-20 01:00 MSK is inside the window that opened yesterday at 22:00.
        Instant now = Instant.parse("2026-11-19T22:00:00Z");
        Instant slot = scheduler.nextSlot(config, now, null, false);
        assertThat(slot).isEqualTo(now);
        assertThat(slot.atZone(ZoneId.of("Europe/Moscow")).getHour()).isEqualTo(1);
    }
}
