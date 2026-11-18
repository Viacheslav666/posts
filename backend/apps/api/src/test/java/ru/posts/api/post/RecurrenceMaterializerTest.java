package ru.posts.api.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.post.PostDtos;

class RecurrenceMaterializerTest {

    @Test
    void onceReturnsSingleOccurrence() {
        Instant start = Instant.parse("2026-11-20T10:00:00Z");
        List<Instant> runs = RecurrenceMaterializer.materialize(
                start, new PostDtos.Recurrence(ApiEnums.RecurrenceFreq.once, 1, null, null), "UTC", start);
        assertThat(runs).containsExactly(start);
    }

    @Test
    void dailyMaterializesWithinHorizon() {
        Instant start = Instant.parse("2026-11-20T10:00:00Z");
        List<Instant> runs = RecurrenceMaterializer.materialize(
                start, new PostDtos.Recurrence(ApiEnums.RecurrenceFreq.daily, 1, null, null), "UTC", start);
        assertThat(runs).hasSize(RecurrenceMaterializer.HORIZON_DAYS + 1);
        assertThat(runs.getFirst()).isEqualTo(start);
        assertThat(runs.getLast()).isEqualTo(start.plus(14, ChronoUnit.DAYS));
    }

    @Test
    void untilCutsHorizon() {
        Instant start = Instant.parse("2026-11-20T10:00:00Z");
        Instant until = start.plus(3, ChronoUnit.DAYS);
        List<Instant> runs = RecurrenceMaterializer.materialize(
                start, new PostDtos.Recurrence(ApiEnums.RecurrenceFreq.daily, 1, null, until), "UTC", start);
        assertThat(runs).hasSize(4);
    }
}
