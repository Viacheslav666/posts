package ru.posts.publisher.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PublicationBackoffTest {

    @Test
    void nextAttemptUsesScheduleAndRetryAfterFloor() {
        Instant now = Instant.parse("2026-11-17T12:00:00Z");
        assertThat(PublicationBackoff.nextAttemptAt(1, now, null)).isEqualTo(now.plusSeconds(5));
        assertThat(PublicationBackoff.nextAttemptAt(2, now, null)).isEqualTo(now.plusSeconds(15));
        assertThat(PublicationBackoff.nextAttemptAt(1, now, Duration.ofSeconds(30)))
                .isEqualTo(now.plusSeconds(30));
    }

    @Test
    void exhaustedByAttemptsOrAge() {
        Instant runAt = Instant.parse("2026-11-17T12:00:00Z");
        assertThat(PublicationBackoff.exhausted(8, runAt, runAt.plusSeconds(10), 8, Duration.ofMinutes(60)))
                .isTrue();
        assertThat(PublicationBackoff.exhausted(
                        1, runAt, runAt.plus(Duration.ofMinutes(61)), 8, Duration.ofMinutes(60)))
                .isTrue();
        assertThat(PublicationBackoff.exhausted(1, runAt, runAt.plusSeconds(10), 8, Duration.ofMinutes(60)))
                .isFalse();
    }
}
