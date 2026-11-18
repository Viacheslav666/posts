package ru.posts.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.common.uuid.UuidV7;

/** BE-23: per-channel remote media attachment cache. */
public class ChannelMediaCacheJdbc {

    public record CachedMedia(String remoteRef, String remoteKind, Instant expiresAt) {}

    private final JdbcClient jdbcClient;

    public ChannelMediaCacheJdbc(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public Optional<CachedMedia> find(UUID channelId, UUID mediaAssetId) {
        return jdbcClient
                .sql(
                        """
                        SELECT remote_ref, remote_kind, expires_at
                        FROM channel_media_cache
                        WHERE channel_id = :channelId
                          AND media_asset_id = :mediaAssetId
                          AND (expires_at IS NULL OR expires_at > now())
                        """)
                .param("channelId", channelId)
                .param("mediaAssetId", mediaAssetId)
                .query((rs, rowNum) -> {
                    Timestamp expires = rs.getTimestamp("expires_at");
                    return new CachedMedia(
                            rs.getString("remote_ref"),
                            rs.getString("remote_kind"),
                            expires == null ? null : expires.toInstant());
                })
                .optional();
    }

    @Transactional
    public void upsert(UUID channelId, UUID mediaAssetId, String remoteRef, String remoteKind, Instant expiresAt) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO channel_media_cache (
                            id, channel_id, media_asset_id, remote_ref, remote_kind, expires_at, created_at, updated_at)
                        VALUES (
                            :id, :channelId, :mediaAssetId, :remoteRef, :remoteKind, :expiresAt, now(), now())
                        ON CONFLICT (channel_id, media_asset_id) DO UPDATE
                        SET remote_ref = EXCLUDED.remote_ref,
                            remote_kind = EXCLUDED.remote_kind,
                            expires_at = EXCLUDED.expires_at,
                            updated_at = now()
                        """)
                .param("id", UuidV7.generate())
                .param("channelId", channelId)
                .param("mediaAssetId", mediaAssetId)
                .param("remoteRef", remoteRef)
                .param("remoteKind", remoteKind == null ? "other" : remoteKind)
                .param("expiresAt", expiresAt == null ? null : Timestamp.from(expiresAt))
                .update();
    }
}
