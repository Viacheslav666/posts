package ru.posts.persistence.jdbc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PublicationPartitionsTest {

    @Test
    void monthAfterTheHardcodedHorizonIsCreated() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PublicationPartitions.ensureMonth(jdbc, "publications", Instant.parse("2028-01-15T00:00:00Z"));
        verify(jdbc)
                .execute(argThat((String sql) -> sql.contains("publications_2028_01")
                        && sql.contains("2028-01-01+00")
                        && sql.contains("2028-02-01+00")));
    }
}
