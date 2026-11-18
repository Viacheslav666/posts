package ru.posts.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.NotificationEntity;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<NotificationEntity> findByUserIdAndWorkspaceIdOrderByCreatedAtDesc(UUID userId, UUID workspaceId);

    List<NotificationEntity> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID userId);

    List<NotificationEntity> findByUserIdAndWorkspaceIdAndReadAtIsNullOrderByCreatedAtDesc(
            UUID userId, UUID workspaceId);

    Optional<NotificationEntity> findByUserIdAndDedupeKey(UUID userId, String dedupeKey);

    @Modifying(clearAutomatically = true)
    @Query(
            """
            UPDATE NotificationEntity n
            SET n.readAt = CURRENT_TIMESTAMP
            WHERE n.userId = :userId
              AND n.readAt IS NULL
              AND (:idsEmpty = true OR n.id IN :ids)
              AND (:workspaceId IS NULL OR n.workspaceId = :workspaceId)
            """)
    int markRead(
            @Param("userId") UUID userId,
            @Param("workspaceId") UUID workspaceId,
            @Param("ids") Collection<UUID> ids,
            @Param("idsEmpty") boolean idsEmpty);
}
