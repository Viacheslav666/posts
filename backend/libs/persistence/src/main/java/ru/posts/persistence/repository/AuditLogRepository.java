package ru.posts.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.AuditLogEntity;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
    List<AuditLogEntity> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    @Query(
            """
            select a from AuditLogEntity a
            where a.workspaceId = :workspaceId
              and (:actorId is null or a.actorId = :actorId)
              and (:action is null or a.action = :action)
              and (:entity is null or a.entity = :entity)
            order by a.createdAt desc, a.id desc
            """)
    List<AuditLogEntity> findFiltered(
            @Param("workspaceId") UUID workspaceId,
            @Param("actorId") UUID actorId,
            @Param("action") String action,
            @Param("entity") String entity);
}
