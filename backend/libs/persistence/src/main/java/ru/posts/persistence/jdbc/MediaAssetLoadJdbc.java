package ru.posts.persistence.jdbc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** Loads ready media_assets rows for the publisher (BE-23). */
public class MediaAssetLoadJdbc {

    public record MediaAssetRow(
            UUID id, String kind, String mime, long sizeBytes, String sha256, String s3Key, String status) {}

    private final JdbcClient jdbcClient;

    public MediaAssetLoadJdbc(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public List<MediaAssetRow> findReady(UUID workspaceId, Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<UUID> ordered = List.copyOf(ids);
        List<MediaAssetRow> found = jdbcClient
                .sql(
                        """
                        SELECT id, kind, mime, coalesce(size_bytes, 0) AS size_bytes, sha256, s3_key, status
                        FROM media_assets
                        WHERE workspace_id = :workspaceId
                          AND deleted_at IS NULL
                          AND id IN (:ids)
                        """)
                .param("workspaceId", workspaceId)
                .param("ids", ordered)
                .query((rs, rowNum) -> new MediaAssetRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("kind"),
                        rs.getString("mime"),
                        rs.getLong("size_bytes"),
                        rs.getString("sha256"),
                        rs.getString("s3_key"),
                        rs.getString("status")))
                .list();
        // Preserve request order.
        List<MediaAssetRow> out = new ArrayList<>();
        for (UUID id : ordered) {
            for (MediaAssetRow row : found) {
                if (row.id().equals(id)) {
                    out.add(row);
                    break;
                }
            }
        }
        return List.copyOf(out);
    }
}
