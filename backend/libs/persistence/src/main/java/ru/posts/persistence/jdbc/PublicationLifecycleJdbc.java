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

/** Hot-path publication state transitions for publisher + reaper (ADR-0004). */
public class PublicationLifecycleJdbc {

    private static final String CLAIM_QUEUED =
            """
            UPDATE publications p
            SET state = 'in_progress',
                attempts = attempts + 1,
                locked_by = :lockedBy,
                locked_until = :lockedUntil
            WHERE p.id = :id
              AND p.state = 'queued'
            RETURNING p.id, p.run_at, p.workspace_id, p.post_id, p.variant_id, p.channel_id,
                      p.network, p.idempotency_key, p.remote_post_id, p.attempts
            """;

    private static final String LOAD_CONTENT =
            """
            SELECT c.external_id AS external_channel_id,
                   coalesce(pv.content ->> 'text', p.base_content ->> 'text', '') AS content_text,
                   coalesce(p.content_kind, 'post') AS content_kind,
                   coalesce(pv.options::text, '{}') AS options_json,
                   coalesce(p.is_ad, false) AS ad,
                   coalesce(p.source_type, 'manual') AS source,
                   oc.erid AS erid,
                   coalesce(oc.advertiser_name, p.ad_payload ->> 'advertiserName') AS advertiser_name,
                   coalesce(oc.advertiser_inn, p.ad_payload ->> 'inn') AS advertiser_inn,
                   oc.display_period_days AS ad_display_period_days,
                   coalesce(oc.auto_delete_after_period, true) AS ad_auto_delete_after_period,
                   p.story_ttl AS story_ttl,
                   p.story_link_text AS story_link_text,
                   p.story_link_url AS story_link_url,
                   coalesce(
                     p.story_options ->> 'mediaKind',
                     pv.content ->> 'mediaKind',
                     CASE
                       WHEN coalesce(pv.content ->> 'text', p.base_content ->> 'text', '') ILIKE '%%video%%'
                         THEN 'video'
                       ELSE 'photo'
                     END
                   ) AS media_kind
            FROM publications pub
            JOIN channels c ON c.id = pub.channel_id
            JOIN posts p ON p.id = pub.post_id
            LEFT JOIN post_variants pv ON pv.id = pub.variant_id
            LEFT JOIN ord_creatives oc ON oc.id = p.ord_creative_id
            WHERE pub.id = :id AND pub.run_at = :runAt
            """;

    private final JdbcClient jdbcClient;

    public PublicationLifecycleJdbc(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public Optional<PublicationClaim> claimQueued(UUID publicationId, String lockedBy, Duration lockTtl) {
        Instant lockedUntil = Instant.now().plus(lockTtl);
        List<PublicationClaim> rows = jdbcClient
                .sql(CLAIM_QUEUED)
                .param("id", publicationId)
                .param("lockedBy", lockedBy)
                .param("lockedUntil", Timestamp.from(lockedUntil))
                .query((rs, rowNum) -> new PublicationClaim(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("run_at").toInstant(),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getObject("post_id", UUID.class),
                        rs.getObject("variant_id", UUID.class),
                        rs.getObject("channel_id", UUID.class),
                        rs.getString("network"),
                        rs.getString("idempotency_key"),
                        null,
                        null,
                        rs.getString("remote_post_id"),
                        rs.getInt("attempts"),
                        "post",
                        "{}",
                        false,
                        "manual",
                        null,
                        null,
                        null,
                        null,
                        true,
                        null,
                        null,
                        null,
                        "photo"))
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        PublicationClaim base = rows.getFirst();
        return Optional.of(enrich(base));
    }

    private PublicationClaim enrich(PublicationClaim base) {
        return jdbcClient
                .sql(LOAD_CONTENT)
                .param("id", base.id())
                .param("runAt", Timestamp.from(base.runAt()))
                .query((rs, rowNum) -> {
                    Integer displayDays =
                            rs.getObject("ad_display_period_days") == null ? null : rs.getInt("ad_display_period_days");
                    return new PublicationClaim(
                            base.id(),
                            base.runAt(),
                            base.workspaceId(),
                            base.postId(),
                            base.variantId(),
                            base.channelId(),
                            base.network(),
                            base.idempotencyKey(),
                            rs.getString("external_channel_id"),
                            rs.getString("content_text"),
                            base.remotePostId(),
                            base.attempts(),
                            rs.getString("content_kind"),
                            rs.getString("options_json"),
                            rs.getBoolean("ad"),
                            rs.getString("source"),
                            rs.getString("erid"),
                            rs.getString("advertiser_name"),
                            rs.getString("advertiser_inn"),
                            displayDays,
                            rs.getBoolean("ad_auto_delete_after_period"),
                            rs.getString("story_ttl"),
                            rs.getString("story_link_text"),
                            rs.getString("story_link_url"),
                            rs.getString("media_kind"));
                })
                .optional()
                .orElse(base);
    }

    @Transactional
    public void markSucceeded(UUID id, Instant runAt, String remotePostId, String remoteUrl) {
        markSucceeded(id, runAt, remotePostId, remoteUrl, null);
    }

    @Transactional
    public void markSucceeded(UUID id, Instant runAt, String remotePostId, String remoteUrl, String metaJson) {
        jdbcClient
                .sql(
                        """
                        UPDATE publications
                        SET state = 'succeeded',
                            remote_post_id = :remotePostId,
                            remote_url = :remoteUrl,
                            published_at = :publishedAt,
                            locked_by = NULL,
                            locked_until = NULL,
                            error_code = NULL,
                            error_message = NULL,
                            next_attempt_at = NULL,
                            meta = CASE
                              WHEN CAST(:metaJson AS text) IS NULL OR CAST(:metaJson AS text) = ''
                                THEN coalesce(meta, '{}'::jsonb)
                              ELSE CAST(:metaJson AS jsonb)
                            END
                        WHERE id = :id AND run_at = :runAt
                        """)
                .param("id", id)
                .param("runAt", Timestamp.from(runAt))
                .param("remotePostId", remotePostId)
                .param("remoteUrl", remoteUrl)
                .param("publishedAt", Timestamp.from(Instant.now()))
                .param("metaJson", metaJson)
                .update();
    }

    @Transactional
    public void markFailedRetryable(
            UUID id, Instant runAt, Instant nextAttemptAt, String errorCode, String errorMessage) {
        jdbcClient
                .sql(
                        """
                        UPDATE publications
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
                        UPDATE publications
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
    public void markChannelReauthRequired(UUID channelId) {
        jdbcClient
                .sql(
                        """
                        UPDATE channels
                        SET status = 'reauth_required', updated_at = now()
                        WHERE id = :channelId
                        """)
                .param("channelId", channelId)
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
                            UPDATE publications
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
                        UPDATE publications
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

    @Transactional
    public void recordAttempt(
            UUID publicationId,
            Instant publicationRunAt,
            int attemptNo,
            Instant startedAt,
            Instant finishedAt,
            Integer httpStatus,
            String errorCode,
            String responseExcerptJson) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO publication_attempts (
                            id, publication_id, publication_run_at, attempt_no,
                            started_at, finished_at, http_status, error_code, response_excerpt)
                        VALUES (
                            :id, :publicationId, :publicationRunAt, :attemptNo,
                            :startedAt, :finishedAt, :httpStatus, :errorCode,
                            CAST(:responseExcerpt AS jsonb))
                        """)
                .param("id", UuidV7.generate())
                .param("publicationId", publicationId)
                .param("publicationRunAt", Timestamp.from(publicationRunAt))
                .param("attemptNo", attemptNo)
                .param("startedAt", Timestamp.from(startedAt))
                .param("finishedAt", finishedAt == null ? null : Timestamp.from(finishedAt))
                .param("httpStatus", httpStatus)
                .param("errorCode", errorCode)
                .param("responseExcerpt", responseExcerptJson == null ? "{}" : responseExcerptJson)
                .update();
    }

    @Transactional(readOnly = true)
    public long countByState(String state) {
        Long count = jdbcClient
                .sql("SELECT count(*) FROM publications WHERE state = :state")
                .param("state", state)
                .query(Long.class)
                .single();
        return count == null ? 0 : count;
    }

    @Transactional(readOnly = true)
    public long countDuePending() {
        Long count = jdbcClient
                .sql(
                        """
                        SELECT count(*) FROM publications
                        WHERE state IN ('pending', 'failed_retryable')
                          AND coalesce(next_attempt_at, run_at) <= now()
                        """)
                .query(Long.class)
                .single();
        return count == null ? 0 : count;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
