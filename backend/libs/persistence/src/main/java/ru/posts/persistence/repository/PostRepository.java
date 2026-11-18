package ru.posts.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.PostEntity;

public interface PostRepository extends JpaRepository<PostEntity, UUID> {

    Optional<PostEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<PostEntity> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);

    List<PostEntity> findByWorkspaceIdAndIdInAndDeletedAtIsNull(UUID workspaceId, Collection<UUID> ids);

    /**
     * Native search with explicit casts so PostgreSQL can type nullable filter params (avoids
     * {@code could not determine data type of parameter $N}).
     */
    @Query(
            value =
                    """
                    SELECT p.* FROM posts p
                    WHERE p.workspace_id = :workspaceId
                      AND p.deleted_at IS NULL
                      AND (CAST(:fromTs AS timestamptz) IS NULL OR p.scheduled_at >= CAST(:fromTs AS timestamptz))
                      AND (CAST(:toTs AS timestamptz) IS NULL OR p.scheduled_at < CAST(:toTs AS timestamptz))
                      AND (CAST(:status AS text) IS NULL OR p.status = CAST(:status AS text))
                      AND (CAST(:projectId AS uuid) IS NULL OR p.project_id = CAST(:projectId AS uuid))
                      AND (CAST(:authorId AS uuid) IS NULL OR p.author_id = CAST(:authorId AS uuid))
                      AND (
                        CAST(:q AS text) IS NULL
                        OR lower(coalesce(p.title, '')) LIKE lower('%' || CAST(:q AS text) || '%')
                      )
                      AND (
                        CAST(:channelId AS uuid) IS NULL
                        OR EXISTS (
                          SELECT 1 FROM post_variants v
                          WHERE v.post_id = p.id AND v.channel_id = CAST(:channelId AS uuid)
                        )
                      )
                      AND (
                        CAST(:network AS text) IS NULL
                        OR EXISTS (
                          SELECT 1 FROM post_variants v
                          WHERE v.post_id = p.id AND v.network = CAST(:network AS text)
                        )
                      )
                    """,
            nativeQuery = true)
    List<PostEntity> search(
            @Param("workspaceId") UUID workspaceId,
            @Param("fromTs") Instant from,
            @Param("toTs") Instant to,
            @Param("status") String status,
            @Param("projectId") UUID projectId,
            @Param("authorId") UUID authorId,
            @Param("channelId") UUID channelId,
            @Param("network") String network,
            @Param("q") String q);

    long countByWorkspaceIdAndCreatedAtGreaterThanEqualAndDeletedAtIsNull(UUID workspaceId, Instant createdAt);
}
