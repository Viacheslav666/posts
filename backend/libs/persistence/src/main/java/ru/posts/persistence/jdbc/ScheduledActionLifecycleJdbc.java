package ru.posts.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.common.uuid.UuidV7;

/** Hot-path state transitions for scheduled_actions (delete/unpin/first_comment/…). */
public class ScheduledActionLifecycleJdbc {

    private static final String CLAIM_QUEUED =
            """
            UPDATE scheduled_actions sa
            SET state = 'in_progress',
                attempts = attempts + 1,
                locked_by = :lockedBy,
                locked_until = :lockedUntil
            WHERE sa.id = :id
              AND sa.state = 'queued'
            RETURNING sa.id, sa.run_at, sa.publication_id, sa.publication_run_at, sa.workspace_id,
                      sa.kind, sa.attempts, sa.payload::text AS payload_json
            """;

    private final JdbcClient jdbcClient;

    public ScheduledActionLifecycleJdbc(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public Optional<ScheduledActionClaim> claimQueued(UUID actionId, String lockedBy, Duration lockTtl) {
        Instant lockedUntil = Instant.now().plus(lockTtl);
        List<ScheduledActionClaim> rows = jdbcClient
                .sql(CLAIM_QUEUED)
                .param("id", actionId)
                .param("lockedBy", lockedBy)
                .param("lockedUntil", Timestamp.from(lockedUntil))
                .query((rs, rowNum) -> new ScheduledActionClaim(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("run_at").toInstant(),
                        rs.getObject("publication_id", UUID.class),
                        rs.getTimestamp("publication_run_at").toInstant(),
                        rs.getObject("workspace_id", UUID.class),
                        null,
                        null,
                        rs.getString("kind"),
                        null,
                        null,
                        rs.getString("payload_json"),
                        rs.getInt("attempts")))
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(enrich(rows.getFirst()));
    }

    private ScheduledActionClaim enrich(ScheduledActionClaim base) {
        return jdbcClient
                .sql(
                        """
                        SELECT p.channel_id, p.network, p.remote_post_id,
                               c.external_id AS external_channel_id
                        FROM publications p
                        JOIN channels c ON c.id = p.channel_id
                        WHERE p.id = :publicationId AND p.run_at = :publicationRunAt
                        """)
                .param("publicationId", base.publicationId())
                .param("publicationRunAt", Timestamp.from(base.publicationRunAt()))
                .query((rs, rowNum) -> new ScheduledActionClaim(
                        base.id(),
                        base.runAt(),
                        base.publicationId(),
                        base.publicationRunAt(),
                        base.workspaceId(),
                        rs.getObject("channel_id", UUID.class),
                        rs.getString("network"),
                        base.kind(),
                        rs.getString("remote_post_id"),
                        rs.getString("external_channel_id"),
                        base.payloadJson(),
                        base.attempts()))
                .optional()
                .orElse(base);
    }

    @Transactional
    public void insertAction(
            UUID publicationId,
            Instant publicationRunAt,
            UUID workspaceId,
            String kind,
            Instant runAt,
            String payloadJson) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO scheduled_actions (
                            id, publication_id, publication_run_at, workspace_id,
                            kind, run_at, state, payload)
                        VALUES (
                            :id, :publicationId, :publicationRunAt, :workspaceId,
                            :kind, :runAt, 'pending', CAST(:payload AS jsonb))
                        """)
                .param("id", UuidV7.generate())
                .param("publicationId", publicationId)
                .param("publicationRunAt", Timestamp.from(publicationRunAt))
                .param("workspaceId", workspaceId)
                .param("kind", kind)
                .param("runAt", Timestamp.from(runAt))
                .param("payload", payloadJson == null ? "{}" : payloadJson)
                .update();
    }

    @Transactional
    public void markSucceeded(UUID id, Instant runAt) {
        jdbcClient
                .sql(
                        """
                        UPDATE scheduled_actions
                        SET state = 'succeeded',
                            completed_at = :completedAt,
                            locked_by = NULL,
                            locked_until = NULL,
                            error_code = NULL,
                            error_message = NULL,
                            next_attempt_at = NULL
                        WHERE id = :id AND run_at = :runAt
                        """)
                .param("id", id)
                .param("runAt", Timestamp.from(runAt))
                .param("completedAt", Timestamp.from(Instant.now()))
                .update();
    }

    @Transactional
    public void markFailedRetryable(
            UUID id, Instant runAt, Instant nextAttemptAt, String errorCode, String errorMessage) {
        jdbcClient
                .sql(
                        """
                        UPDATE scheduled_actions
                        SET state = 'failed_retryable',
                            next_attempt_at = :nextAttemptAt,
                            error_code = :errorCode,
                            error_message = :errorMessage,
                            locked_by = NULL,
                            locked_until = NULL
                        WHERE id = :id AND run_at = :runAt
                        """)
                .param("id", id)
                .param("runAt", Timestamp.from(runAt))
                .param("nextAttemptAt", Timestamp.from(nextAttemptAt))
                .param("errorCode", errorCode)
                .param("errorMessage", truncate(errorMessage, 1000))
                .update();
    }

    @Transactional
    public void markFailedPermanent(UUID id, Instant runAt, String errorCode, String errorMessage) {
        jdbcClient
                .sql(
                        """
                        UPDATE scheduled_actions
                        SET state = 'failed_permanent',
                            error_code = :errorCode,
                            error_message = :errorMessage,
                            locked_by = NULL,
                            locked_until = NULL,
                            next_attempt_at = NULL
                        WHERE id = :id AND run_at = :runAt
                        """)
                .param("id", id)
                .param("runAt", Timestamp.from(runAt))
                .param("errorCode", errorCode)
                .param("errorMessage", truncate(errorMessage, 1000))
                .update();
    }

    @Transactional
    public void revertQueuedToPending(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (UUID id : ids) {
            jdbcClient
                    .sql(
                            """
                            UPDATE scheduled_actions
                            SET state = 'pending', locked_until = NULL, locked_by = NULL
                            WHERE id = :id AND state = 'queued'
                            """)
                    .param("id", id)
                    .update();
        }
    }

    @Transactional
    public int reaperStuck() {
        return jdbcClient
                .sql(
                        """
                        UPDATE scheduled_actions
                        SET state = 'failed_retryable',
                            next_attempt_at = now(),
                            locked_by = NULL,
                            locked_until = NULL,
                            error_code = 'reaper_stale_lock',
                            error_message = 'locked_until expired'
                        WHERE state IN ('queued', 'in_progress')
                          AND locked_until IS NOT NULL
                          AND locked_until < now()
                        """)
                .update();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
