package ru.posts.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.common.uuid.UuidV7;

/** Hot-path state transitions for social_remote_actions (BE-33). */
public class SocialRemoteActionLifecycleJdbc {

    private static final String CLAIM_QUEUED =
            """
            UPDATE social_remote_actions sra
            SET state = 'in_progress',
                attempts = attempts + 1,
                locked_by = :lockedBy,
                locked_until = :lockedUntil
            WHERE sra.id = :id
              AND sra.state = 'queued'
            RETURNING sra.id, sra.run_at, sra.workspace_id, sra.channel_id, sra.kind,
                      sra.target_ref::text AS target_json,
                      sra.payload::text AS payload_json,
                      sra.idempotency_key, sra.source,
                      sra.source_ref::text AS source_ref_json,
                      sra.attempts,
                      (SELECT c.network FROM channels c WHERE c.id = sra.channel_id) AS network,
                      (SELECT c.external_id FROM channels c WHERE c.id = sra.channel_id) AS external_channel_id
            """;

    private final JdbcClient jdbcClient;

    public SocialRemoteActionLifecycleJdbc(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Inserts a pending action. On idempotency conflict returns the existing id (same key, any
     * partition).
     */
    @Transactional
    public UUID enqueue(
            String kind,
            UUID workspaceId,
            UUID channelId,
            String targetJson,
            String payloadJson,
            String idempotencyKey,
            Instant runAt,
            String source,
            String sourceRefJson,
            UUID createdBy) {
        // Unique index is (idempotency_key, run_at) because the table is partitioned by run_at.
        // Serialize same-key enqueues so a second run_at cannot slip in between the lookup and insert.
        jdbcClient
                .sql("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:key AS text), 0))")
                .param("key", idempotencyKey == null ? "" : idempotencyKey)
                .query((rs, rowNum) -> 1)
                .list();
        Optional<UUID> existing = findIdByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID id = UuidV7.generate();
        int inserted = jdbcClient
                .sql(
                        """
                        INSERT INTO social_remote_actions (
                            id, workspace_id, channel_id, kind, target_ref, payload,
                            idempotency_key, state, run_at, source, source_ref, created_by)
                        VALUES (
                            :id, :workspaceId, :channelId, :kind,
                            CAST(:targetJson AS jsonb), CAST(:payloadJson AS jsonb),
                            :idempotencyKey, 'pending', :runAt, :source,
                            CAST(:sourceRefJson AS jsonb), :createdBy)
                        ON CONFLICT (idempotency_key, run_at) DO NOTHING
                        """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("channelId", channelId)
                .param("kind", kind)
                .param("targetJson", targetJson == null ? "{}" : targetJson)
                .param("payloadJson", payloadJson == null ? "{}" : payloadJson)
                .param("idempotencyKey", idempotencyKey)
                .param("runAt", Timestamp.from(runAt))
                .param("source", source)
                .param("sourceRefJson", sourceRefJson == null ? "{}" : sourceRefJson)
                .param("createdBy", createdBy)
                .update();
        if (inserted == 0) {
            return findIdByIdempotencyKey(idempotencyKey).orElse(id);
        }
        return id;
    }

    public record ActionRef(UUID id, String state, Instant runAt) {}

    @Transactional(readOnly = true)
    public Optional<ActionRef> findLatest(String idempotencyKey) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, state, run_at
                        FROM social_remote_actions
                        WHERE idempotency_key = :idempotencyKey
                        ORDER BY created_at DESC
                        LIMIT 1
                        """)
                .param("idempotencyKey", idempotencyKey)
                .query((rs, rowNum) -> new ActionRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("state"),
                        rs.getTimestamp("run_at").toInstant()))
                .optional();
    }

    /** Cancels a not-yet-started action. Returns false when it is already queued or running. */
    @Transactional
    public boolean cancelIfIdle(String idempotencyKey) {
        int updated = jdbcClient
                .sql(
                        """
                        UPDATE social_remote_actions
                        SET state = 'cancelled',
                            error_code = 'cancelled',
                            error_message = 'cancelled before execution',
                            completed_at = now(),
                            locked_by = NULL,
                            locked_until = NULL,
                            next_attempt_at = NULL
                        WHERE idempotency_key = :idempotencyKey
                          AND state IN ('pending', 'failed_retryable')
                        """)
                .param("idempotencyKey", idempotencyKey)
                .update();
        return updated > 0;
    }

    /**
     * Put an in-progress action back to pending without burning an attempt (prerequisite not done).
     */
    @Transactional
    public void deferWaiting(UUID id, Instant runAt, Instant nextAttemptAt) {
        jdbcClient
                .sql(
                        """
                        UPDATE social_remote_actions
                        SET state = 'pending',
                            next_attempt_at = :nextAttemptAt,
                            attempts = GREATEST(attempts - 1, 0),
                            error_code = 'waiting_prerequisite',
                            error_message = 'waiting for prerequisite action',
                            locked_by = NULL,
                            locked_until = NULL
                        WHERE id = :id AND run_at = :runAt
                        """)
                .param("id", id)
                .param("runAt", Timestamp.from(runAt))
                .param("nextAttemptAt", Timestamp.from(nextAttemptAt))
                .update();
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findIdByIdempotencyKey(String idempotencyKey) {
        return jdbcClient
                .sql(
                        """
                        SELECT id
                        FROM social_remote_actions
                        WHERE idempotency_key = :idempotencyKey
                        ORDER BY created_at
                        LIMIT 1
                        """)
                .param("idempotencyKey", idempotencyKey)
                .query((rs, rowNum) -> rs.getObject("id", UUID.class))
                .optional();
    }

    @Transactional
    public Optional<SocialRemoteActionClaim> claimQueued(UUID actionId, String lockedBy, Duration lockTtl) {
        Instant lockedUntil = Instant.now().plus(lockTtl);
        List<SocialRemoteActionClaim> rows = jdbcClient
                .sql(CLAIM_QUEUED)
                .param("id", actionId)
                .param("lockedBy", lockedBy)
                .param("lockedUntil", Timestamp.from(lockedUntil))
                .query((rs, rowNum) -> mapClaim(rs))
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    @Transactional
    public void markSucceeded(UUID id, Instant runAt, String resultJson) {
        jdbcClient
                .sql(
                        """
                        UPDATE social_remote_actions
                        SET state = 'succeeded',
                            result = CASE
                              WHEN CAST(:resultJson AS text) IS NULL OR CAST(:resultJson AS text) = ''
                                THEN result
                              ELSE CAST(:resultJson AS jsonb)
                            END,
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
                .param("resultJson", resultJson)
                .param("completedAt", Timestamp.from(Instant.now()))
                .update();
    }

    @Transactional
    public void markFailedRetryable(
            UUID id, Instant runAt, Instant nextAttemptAt, String errorCode, String errorMessage) {
        jdbcClient
                .sql(
                        """
                        UPDATE social_remote_actions
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
                        UPDATE social_remote_actions
                        SET state = 'failed_permanent',
                            error_code = :errorCode,
                            error_message = :errorMessage,
                            locked_by = NULL,
                            locked_until = NULL,
                            next_attempt_at = NULL,
                            completed_at = :completedAt
                        WHERE id = :id AND run_at = :runAt
                        """)
                .param("id", id)
                .param("runAt", Timestamp.from(runAt))
                .param("errorCode", errorCode)
                .param("errorMessage", truncate(errorMessage, 1000))
                .param("completedAt", Timestamp.from(Instant.now()))
                .update();
    }

    @Transactional
    public void markUnknownOutcome(UUID id, Instant runAt, String errorCode, String errorMessage) {
        jdbcClient
                .sql(
                        """
                        UPDATE social_remote_actions
                        SET state = 'unknown_outcome',
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
    public void markCancelled(UUID id, Instant runAt, String errorCode, String errorMessage) {
        jdbcClient
                .sql(
                        """
                        UPDATE social_remote_actions
                        SET state = 'cancelled',
                            error_code = :errorCode,
                            error_message = :errorMessage,
                            locked_by = NULL,
                            locked_until = NULL,
                            next_attempt_at = NULL,
                            completed_at = :completedAt
                        WHERE id = :id AND run_at = :runAt
                        """)
                .param("id", id)
                .param("runAt", Timestamp.from(runAt))
                .param("errorCode", errorCode)
                .param("errorMessage", truncate(errorMessage, 1000))
                .param("completedAt", Timestamp.from(Instant.now()))
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
                            UPDATE social_remote_actions
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
                        UPDATE social_remote_actions
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

    @Transactional(readOnly = true)
    public int countPendingForChannel(UUID channelId) {
        Integer count = jdbcClient
                .sql(
                        """
                        SELECT count(*)::int
                        FROM social_remote_actions
                        WHERE channel_id = :channelId
                          AND state IN ('pending', 'failed_retryable', 'queued', 'in_progress', 'unknown_outcome')
                        """)
                .param("channelId", channelId)
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }

    /** Alias for pending/queued/in-progress depth used by VK execute batching (BE-33). */
    @Transactional(readOnly = true)
    public int countPendingOrQueuedForChannel(UUID channelId) {
        return countPendingForChannel(channelId);
    }

    /**
     * Pending load across channels that share one access token (caller resolves channel set).
     */
    @Transactional(readOnly = true)
    public int countPendingForToken(Collection<UUID> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (UUID channelId : channelIds) {
            total += countPendingForChannel(channelId);
        }
        return total;
    }

    @Transactional(readOnly = true)
    public int countQueuedForChannel(UUID channelId) {
        Integer count = jdbcClient
                .sql(
                        """
                        SELECT count(*)::int
                        FROM social_remote_actions
                        WHERE channel_id = :channelId
                          AND state = 'queued'
                        """)
                .param("channelId", channelId)
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }

    /** Due pending/retryable rows for VK {@code execute} batching (does not claim). */
    @Transactional(readOnly = true)
    public List<SocialRemoteActionClaim> findDueForBatch(UUID channelId, int limit) {
        return jdbcClient
                .sql(
                        """
                        SELECT sra.id, sra.run_at, sra.workspace_id, sra.channel_id, sra.kind,
                               sra.target_ref::text AS target_json,
                               sra.payload::text AS payload_json,
                               sra.idempotency_key, sra.source,
                               sra.source_ref::text AS source_ref_json,
                               sra.attempts,
                               c.network,
                               c.external_id AS external_channel_id
                        FROM social_remote_actions sra
                        JOIN channels c ON c.id = sra.channel_id
                        WHERE sra.channel_id = :channelId
                          AND sra.state IN ('pending', 'failed_retryable')
                          AND coalesce(sra.next_attempt_at, sra.run_at) <= now()
                        ORDER BY coalesce(sra.next_attempt_at, sra.run_at)
                        LIMIT :limit
                        """)
                .param("channelId", channelId)
                .param("limit", limit)
                .query((rs, rowNum) -> mapClaim(rs))
                .list();
    }

    /**
     * Queued sibling action ids for the same channel (VK {@code execute} batching), excluding the
     * already-claimed primary.
     */
    @Transactional(readOnly = true)
    public List<UUID> findQueuedIdsForChannel(UUID channelId, UUID excludeId, int limit) {
        return jdbcClient
                .sql(
                        """
                        SELECT id
                        FROM social_remote_actions
                        WHERE channel_id = :channelId
                          AND state = 'queued'
                          AND id <> :excludeId
                        ORDER BY run_at
                        LIMIT :limit
                        """)
                .param("channelId", channelId)
                .param("excludeId", excludeId)
                .param("limit", limit)
                .query((rs, rowNum) -> rs.getObject("id", UUID.class))
                .list();
    }

    private static SocialRemoteActionClaim mapClaim(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SocialRemoteActionClaim(
                rs.getObject("id", UUID.class),
                rs.getTimestamp("run_at").toInstant(),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("channel_id", UUID.class),
                rs.getString("network"),
                rs.getString("external_channel_id"),
                rs.getString("kind"),
                rs.getString("target_json"),
                rs.getString("payload_json"),
                rs.getString("idempotency_key"),
                rs.getString("source"),
                rs.getString("source_ref_json"),
                rs.getInt("attempts"));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
