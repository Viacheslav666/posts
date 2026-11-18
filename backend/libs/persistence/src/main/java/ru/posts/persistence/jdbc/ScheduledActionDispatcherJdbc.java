package ru.posts.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** SKIP LOCKED dispatcher for {@code scheduled_actions} (02-backend.md §5.5). */
public class ScheduledActionDispatcherJdbc {

    private static final String CLAIM_DUE =
            """
            WITH due AS (
              SELECT sa.id, sa.run_at
              FROM scheduled_actions sa
              WHERE sa.state IN ('pending', 'failed_retryable')
                AND coalesce(sa.next_attempt_at, sa.run_at) <= :horizon
              ORDER BY coalesce(sa.next_attempt_at, sa.run_at)
              LIMIT :batchSize
              FOR UPDATE SKIP LOCKED
            )
            UPDATE scheduled_actions sa
            SET state = 'queued',
                locked_until = :lockedUntil
            FROM due
            WHERE sa.id = due.id AND sa.run_at = due.run_at
            RETURNING sa.id, sa.run_at, sa.workspace_id,
                      (SELECT p.network FROM publications p
                       WHERE p.id = sa.publication_id AND p.run_at = sa.publication_run_at) AS network
            """;

    private final JdbcClient jdbcClient;

    public ScheduledActionDispatcherJdbc(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public List<ScheduledActionDispatchRow> claimDue(int batchSize, Duration lockTtl, Duration lookAhead) {
        Instant horizon = Instant.now().plus(lookAhead);
        Instant lockedUntil = Instant.now().plus(lockTtl);
        return jdbcClient
                .sql(CLAIM_DUE)
                .param("horizon", Timestamp.from(horizon))
                .param("batchSize", batchSize)
                .param("lockedUntil", Timestamp.from(lockedUntil))
                .query((rs, rowNum) -> new ScheduledActionDispatchRow(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("run_at").toInstant(),
                        rs.getString("network"),
                        rs.getObject("workspace_id", UUID.class)))
                .list();
    }
}
