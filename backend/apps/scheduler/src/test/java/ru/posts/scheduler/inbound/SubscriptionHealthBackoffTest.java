package ru.posts.scheduler.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class SubscriptionHealthBackoffTest {

    @Test
    void justFailedSubscriptionIsSkippedUntilBackoffElapses() {
        Instant now = Instant.parse("2026-11-19T12:00:00Z");
        assertThat(SubscriptionHealthJob.dueForRepair("failed", 1, now.minus(1, ChronoUnit.MINUTES), now))
                .isFalse();
        assertThat(SubscriptionHealthJob.dueForRepair("failed", 1, now.minus(6, ChronoUnit.MINUTES), now))
                .isTrue();
        assertThat(SubscriptionHealthJob.dueForRepair("active", 3, now, now)).isTrue();
    }
}
