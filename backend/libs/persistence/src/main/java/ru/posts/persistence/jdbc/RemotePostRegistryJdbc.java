package ru.posts.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.common.uuid.UuidV7;

/** Own-post registry + publish intents (BE-33 L1/L2). */
public class RemotePostRegistryJdbc {

    private final JdbcClient jdbcClient;

    public RemotePostRegistryJdbc(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public UUID upsert(
            UUID workspaceId,
            String network,
            UUID channelId,
            String remotePostId,
            String origin,
            UUID publicationId,
            UUID postId,
            String rootOriginJson,
            short hop,
            String contentFingerprint) {
        UUID id = UuidV7.generate();
        return jdbcClient
                .sql(
                        """
                        INSERT INTO remote_post_registry (
                            id, network, channel_id, remote_post_id, origin,
                            publication_id, post_id, root_origin, hop,
                            content_fingerprint, workspace_id)
                        VALUES (
                            :id, :network, :channelId, :remotePostId, :origin,
                            :publicationId, :postId, CAST(:rootOriginJson AS jsonb), :hop,
                            :contentFingerprint, :workspaceId)
                        ON CONFLICT (channel_id, remote_post_id) DO UPDATE
                        SET origin = EXCLUDED.origin,
                            publication_id = coalesce(EXCLUDED.publication_id, remote_post_registry.publication_id),
                            post_id = coalesce(EXCLUDED.post_id, remote_post_registry.post_id),
                            root_origin = CASE
                              WHEN CAST(:rootOriginJson AS text) IS NULL OR CAST(:rootOriginJson AS text) = ''
                                   OR CAST(:rootOriginJson AS text) = '{}'
                                THEN remote_post_registry.root_origin
                              ELSE EXCLUDED.root_origin
                            END,
                            hop = EXCLUDED.hop,
                            content_fingerprint = coalesce(
                              EXCLUDED.content_fingerprint, remote_post_registry.content_fingerprint)
                        RETURNING id
                        """)
                .param("id", id)
                .param("network", network)
                .param("channelId", channelId)
                .param("remotePostId", remotePostId)
                .param("origin", origin)
                .param("publicationId", publicationId)
                .param("postId", postId)
                .param("rootOriginJson", rootOriginJson == null ? "{}" : rootOriginJson)
                .param("hop", hop)
                .param("contentFingerprint", contentFingerprint)
                .param("workspaceId", workspaceId)
                .query((rs, rowNum) -> rs.getObject("id", UUID.class))
                .single();
    }

    @Transactional(readOnly = true)
    public boolean isOwn(UUID channelId, String remotePostId) {
        return findOwn(channelId, remotePostId).isPresent();
    }

    /** L1 lookup including origin/hop for crosspost loop decisions. */
    @Transactional(readOnly = true)
    public Optional<RegistryEntry> findOwn(UUID channelId, String remotePostId) {
        return jdbcClient
                .sql(
                        """
                        SELECT origin, hop, content_fingerprint, root_origin::text AS root_origin
                        FROM remote_post_registry
                        WHERE channel_id = :channelId
                          AND remote_post_id = :remotePostId
                        LIMIT 1
                        """)
                .param("channelId", channelId)
                .param("remotePostId", remotePostId)
                .query((rs, rowNum) -> new RegistryEntry(
                        rs.getString("origin"),
                        rs.getShort("hop"),
                        rs.getString("content_fingerprint"),
                        rs.getString("root_origin")))
                .optional();
    }

    /** Recent fingerprints on a target channel (L4 content dedup, 24h window). */
    @Transactional(readOnly = true)
    public List<String> listRecentFingerprints(UUID channelId, Instant since, int limit) {
        return jdbcClient
                .sql(
                        """
                        SELECT content_fingerprint
                        FROM remote_post_registry
                        WHERE channel_id = :channelId
                          AND content_fingerprint IS NOT NULL
                          AND created_at >= :since
                        ORDER BY created_at DESC
                        LIMIT :limit
                        """)
                .param("channelId", channelId)
                .param("since", Timestamp.from(since))
                .param("limit", limit)
                .query(String.class)
                .list();
    }

    public record RegistryEntry(String origin, short hop, String contentFingerprint, String rootOriginJson) {}

    @Transactional(readOnly = true)
    public boolean isOwnByFingerprint(UUID channelId, String fingerprint, Instant since) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return false;
        }
        Integer count = jdbcClient
                .sql(
                        """
                        SELECT 1
                        FROM remote_post_registry
                        WHERE channel_id = :channelId
                          AND content_fingerprint = :fingerprint
                          AND created_at >= :since
                        LIMIT 1
                        """)
                .param("channelId", channelId)
                .param("fingerprint", fingerprint)
                .param("since", Timestamp.from(since))
                .query(Integer.class)
                .optional()
                .orElse(null);
        return count != null;
    }

    @Transactional
    public void insertIntent(UUID channelId, String idempotencyKey, String contentFingerprint, Instant expiresAt) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO publish_intents (
                            channel_id, idempotency_key, content_fingerprint, expires_at)
                        VALUES (:channelId, :idempotencyKey, :contentFingerprint, :expiresAt)
                        ON CONFLICT (channel_id, idempotency_key) DO UPDATE
                        SET content_fingerprint = EXCLUDED.content_fingerprint,
                            expires_at = EXCLUDED.expires_at
                        """)
                .param("channelId", channelId)
                .param("idempotencyKey", idempotencyKey)
                .param("contentFingerprint", contentFingerprint)
                .param("expiresAt", Timestamp.from(expiresAt))
                .update();
    }

    @Transactional
    public void consumeIntent(UUID channelId, String idempotencyKey) {
        jdbcClient
                .sql(
                        """
                        DELETE FROM publish_intents
                        WHERE channel_id = :channelId
                          AND idempotency_key = :idempotencyKey
                        """)
                .param("channelId", channelId)
                .param("idempotencyKey", idempotencyKey)
                .update();
    }

    @Transactional(readOnly = true)
    public boolean isIntentOwn(UUID channelId, String contentFingerprint) {
        if (contentFingerprint == null || contentFingerprint.isBlank()) {
            return false;
        }
        Integer count = jdbcClient
                .sql(
                        """
                        SELECT 1
                        FROM publish_intents
                        WHERE channel_id = :channelId
                          AND content_fingerprint = :contentFingerprint
                          AND expires_at > now()
                        LIMIT 1
                        """)
                .param("channelId", channelId)
                .param("contentFingerprint", contentFingerprint)
                .query(Integer.class)
                .optional()
                .orElse(null);
        return count != null;
    }

    @Transactional(readOnly = true)
    public boolean isIntentOwnByKey(UUID channelId, String idempotencyKey) {
        Integer count = jdbcClient
                .sql(
                        """
                        SELECT 1
                        FROM publish_intents
                        WHERE channel_id = :channelId
                          AND idempotency_key = :idempotencyKey
                          AND expires_at > now()
                        LIMIT 1
                        """)
                .param("channelId", channelId)
                .param("idempotencyKey", idempotencyKey)
                .query(Integer.class)
                .optional()
                .orElse(null);
        return count != null;
    }

    @Transactional
    public int deleteExpiredIntents() {
        return jdbcClient
                .sql(
                        """
                        DELETE FROM publish_intents
                        WHERE expires_at < now()
                        """)
                .update();
    }

    /** Retention purge for registry rows older than {@code olderThan} (typically now - 90d). */
    @Transactional
    public int deleteOldRegistry(Instant olderThan) {
        return jdbcClient
                .sql(
                        """
                        DELETE FROM remote_post_registry
                        WHERE created_at < :olderThan
                        """)
                .param("olderThan", Timestamp.from(olderThan))
                .update();
    }
}
