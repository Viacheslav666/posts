package ru.posts.social.inbound.dedup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PostgresTimestampTest {

    @Test
    void roundsToTheMicrosecondPostgresStores() {
        Instant raw = Instant.parse("2026-11-19T08:50:22.184726839Z");
        assertThat(InboundEventStore.postgresTimestamp(raw)).isEqualTo(Instant.parse("2026-11-19T08:50:22.184727Z"));
    }

    @Test
    void carriesARoundedMicrosecondIntoTheNextSecond() {
        Instant raw = Instant.parse("2026-11-19T08:50:22.999999500Z");
        assertThat(InboundEventStore.postgresTimestamp(raw)).isEqualTo(Instant.parse("2026-11-19T08:50:23Z"));
    }
}
