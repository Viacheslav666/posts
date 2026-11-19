package ru.posts.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** SKIP LOCKED dispatcher for {@code social_remote_actions} (BE-33). */
public class SocialRemoteActionDispatcherJdbc {

    private static final String CLAIM_DUE =
            """
            WITH due AS (
              SELECT sra.id, sra.run_at
              FROM social_remote_actions sra
              WHERE sra.state IN ('pending', 'failed_retryable')
                AND coalesce(sra.next_attempt_at, sra.run_at) <= :horizon
              ORDER BY coalesce(sra.next_attempt_at, sra.run_at)
              LIMIT :batchSize
              FOR UPDATE SKIP LOCKED
            )
            UPDATE social_remote_actions sra
            SET state = 'queued',
                locked_until = :lockedUntil
            FROM due
            WHERE sra.id = due.id AND sra.run_at = due.run_at
            RETURNING sra.id, sra.run_at, sra.workspace_id, sra.channel_id,
                      (SELECT c.network FROM channels c WHERE c.id = sra.channel_id) AS network
            """;

    private final JdbcClient jdbcClient;

    public SocialRemoteActionDispatcherJdbc(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public List<SocialRemoteActionDispatchRow> claimDue(int batchSize, Duration lockTtl, Duration lookAhead) {
        Instant horizon = Instant.now().plus(lookAhead);
        Instant lockedUntil = Instant.now().plus(lockTtl);
        return jdbcClient
                .sql(CLAIM_DUE)
                .param("horizon", Timestamp.from(horizon))
                .param("batchSize", batchSize)
                .param("lockedUntil", Timestamp.from(lockedUntil))
                .query((rs, rowNum) -> new SocialRemoteActionDispatchRow(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("run_at").toInstant(),
                        rs.getString("network"),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getObject("channel_id", UUID.class)))
                .list();
    }
}
