package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.MediaAssetEntity;

public interface MediaAssetRepository extends JpaRepository<MediaAssetEntity, UUID> {

    List<MediaAssetEntity> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);

    Optional<MediaAssetEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<MediaAssetEntity> findByWorkspaceIdAndIdAndDeletedAtIsNull(UUID workspaceId, UUID id);

    @Query(
            """
            select coalesce(sum(m.sizeBytes), 0) from MediaAssetEntity m
            where m.workspaceId = :workspaceId and m.deletedAt is null
            """)
    long sumSizeBytesByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    long countByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);

    @Query(
            """
            select distinct m.folder from MediaAssetEntity m
            where m.workspaceId = :workspaceId
              and m.deletedAt is null
              and m.folder is not null
              and m.folder <> ''
            order by m.folder asc
            """)
    List<String> findDistinctFoldersByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query(
            value =
                    """
                    SELECT * FROM media_assets m
                    WHERE m.workspace_id = :workspaceId
                      AND m.deleted_at IS NULL
                      AND (:kind IS NULL OR m.kind = :kind)
                      AND (:status IS NULL OR m.status = :status)
                      AND (:folder IS NULL OR m.folder = :folder)
                      AND (:tag IS NULL OR :tag = ANY (m.tags))
                      AND (
                        :q IS NULL
                        OR m.filename ILIKE CONCAT('%', :q, '%')
                      )
                    ORDER BY m.id DESC
                    """,
            nativeQuery = true)
    List<MediaAssetEntity> search(
            @Param("workspaceId") UUID workspaceId,
            @Param("kind") String kind,
            @Param("status") String status,
            @Param("folder") String folder,
            @Param("tag") String tag,
            @Param("q") String q);
}
