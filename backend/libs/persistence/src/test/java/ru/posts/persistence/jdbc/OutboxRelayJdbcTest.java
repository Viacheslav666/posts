package ru.posts.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxRelayJdbcTest {

    @Test
    void publishedAtIsBoundAsSqlTimestamp() {
        Instant instant = Instant.parse("2026-11-26T00:54:00Z");
        Timestamp bound = OutboxRelayJdbc.publishedAtParameter(instant);
        assertThat(bound).isInstanceOf(Timestamp.class);
        assertThat(bound.toInstant()).isEqualTo(instant);
        assertThat(bound).isNotInstanceOf(Instant.class);
    }
}
