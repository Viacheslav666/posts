package ru.posts.api.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.post.PostDtos;

class RecurrenceMaterializerTimesTest {

    @Test
    void timesExpandsMultipleRunsPerDay() {
        Instant start = Instant.parse("2026-11-20T00:00:00Z");
        // until is inclusive of the Instant itself; use end-of-day window for two calendar days.
        Instant until = Instant.parse("2026-11-21T23:59:59Z");
        List<Instant> runs = RecurrenceMaterializer.materialize(
                start,
                new PostDtos.Recurrence(ApiEnums.RecurrenceFreq.daily, 1, null, until, List.of("09:00", "18:30")),
                "UTC",
                start);
        assertThat(runs)
                .containsExactly(
                        Instant.parse("2026-11-20T09:00:00Z"),
                        Instant.parse("2026-11-20T18:30:00Z"),
                        Instant.parse("2026-11-21T09:00:00Z"),
                        Instant.parse("2026-11-21T18:30:00Z"));
    }

    @Test
    void withoutTimesKeepsLegacyScheduledAtTime() {
        Instant start = Instant.parse("2026-11-20T10:00:00Z");
        List<Instant> runs = RecurrenceMaterializer.materialize(
                start, new PostDtos.Recurrence(ApiEnums.RecurrenceFreq.daily, 1, null, null, null), "UTC", start);
        assertThat(runs.getFirst()).isEqualTo(start);
        assertThat(runs.get(1)).isEqualTo(start.plus(1, ChronoUnit.DAYS));
    }

    @Test
    void onceWithTimesEmitsSameDaySlots() {
        Instant start = Instant.parse("2026-11-20T10:00:00Z");
        List<Instant> runs = RecurrenceMaterializer.materialize(
                start,
                new PostDtos.Recurrence(ApiEnums.RecurrenceFreq.once, 1, null, null, List.of("08:00", "20:00")),
                "UTC",
                start);
        assertThat(runs).containsExactly(Instant.parse("2026-11-20T08:00:00Z"), Instant.parse("2026-11-20T20:00:00Z"));
    }

    @Test
    void timesRespectPostTimezone() {
        Instant start = Instant.parse("2026-11-20T00:00:00Z");
        List<Instant> runs = RecurrenceMaterializer.materialize(
                start,
                new PostDtos.Recurrence(ApiEnums.RecurrenceFreq.once, 1, null, null, List.of("12:00")),
                "Europe/Moscow",
                start);
        // 12:00 Moscow = 09:00 UTC
        assertThat(runs).containsExactly(Instant.parse("2026-11-20T09:00:00Z"));
    }
}
