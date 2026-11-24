package ru.posts.grabber.target;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.posts.grabber.config.GrabberConfigSupport;

class GrabberDailyWindowTest {

    @Test
    void countsCalendarDayInScheduleTimezone() {
        Instant now = Instant.parse("2026-11-18T21:30:00Z");
        Map<String, Object> samara =
                GrabberConfigSupport.withDefaults(Map.of("schedule", Map.of("timezone", "Europe/Samara")));
        Instant samaraStart = GrabberDailyWindow.start(samara, now);
        assertThat(samaraStart).isEqualTo(Instant.parse("2026-11-18T20:00:00Z"));
        assertThat(samaraStart).isNotEqualTo(now.minusSeconds(24 * 3600));

        Instant moscowStart = GrabberDailyWindow.start(GrabberConfigSupport.withDefaults(Map.of()), now);
        assertThat(moscowStart).isEqualTo(Instant.parse("2026-11-18T21:00:00Z"));
    }

    @Test
    void quotaIgnoresPendingModerationAndReopensRateLimitSkips() {
        assertThat(GrabberDailyWindow.QUOTA_STATUSES).containsExactly("published", "scheduled");
        assertThat(GrabberDailyWindow.QUOTA_STATUSES).doesNotContain("pending_moderation", "approved");
        assertThat(GrabberDailyWindow.isReopenableRateLimit("filtered_out", "rate_limit_per_day"))
                .isTrue();
        assertThat(GrabberDailyWindow.isReopenableRateLimit("filtered_out", "rate_limit_per_source_per_day"))
                .isTrue();
        assertThat(GrabberDailyWindow.isReopenableRateLimit("filtered_out", "max_age_days"))
                .isFalse();
        assertThat(GrabberDailyWindow.isReopenableRateLimit("published", "rate_limit_per_day"))
                .isFalse();
    }
}
