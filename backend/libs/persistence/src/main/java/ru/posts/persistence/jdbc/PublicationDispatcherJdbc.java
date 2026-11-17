package ru.posts.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hot-path dispatcher queries (ADR-0004 / ADR-0007). Uses {@link JdbcClient} + {@code FOR UPDATE
 * SKIP LOCKED}, not JPA.
 */
public class PublicationDispatcherJdbc {

    private static final String CLAIM_DUE =
            """
            WITH due AS (
              SELECT id, run_at
              FROM publications
              WHERE state IN ('pending', 'failed_retryable')
                AND coalesce(next_attempt_at, run_at) <= :horizon
              ORDER BY coalesce(next_attempt_at, run_at)
              LIMIT :batchSize
              FOR UPDATE SKIP LOCKED
            )
            UPDATE publications p
            SET state = 'queued',
                locked_until = :lockedUntil
            FROM due
            WHERE p.id = due.id AND p.run_at = due.run_at
            RETURNING p.id, p.run_at, p.network, p.workspace_id
            """;

    private final JdbcClient jdbcClient;

    public PublicationDispatcherJdbc(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public List<PublicationDispatchRow> claimDue(int batchSize, Duration lockTtl, Duration lookAhead) {
        Instant horizon = Instant.now().plus(lookAhead);
        Instant lockedUntil = Instant.now().plus(lockTtl);
        return jdbcClient
                .sql(CLAIM_DUE)
                .param("horizon", Timestamp.from(horizon))
                .param("batchSize", batchSize)
                .param("lockedUntil", Timestamp.from(lockedUntil))
                .query((rs, rowNum) -> new PublicationDispatchRow(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("run_at").toInstant(),
                        rs.getString("network"),
                        rs.getObject("workspace_id", UUID.class)))
                .list();
    }
}
