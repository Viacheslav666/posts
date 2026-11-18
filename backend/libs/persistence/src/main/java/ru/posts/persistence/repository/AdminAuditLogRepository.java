package ru.posts.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.AdminAuditLogEntity;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLogEntity, UUID> {

    @Query(
            """
            select a from AdminAuditLogEntity a
            where (:actorId is null or a.actorUserId = :actorId)
              and (:action is null or a.action = :action)
              and (:targetType is null or a.targetType = :targetType)
              and (:targetId is null or a.targetId = :targetId)
            order by a.createdAt desc, a.id desc
            """)
    List<AdminAuditLogEntity> findFiltered(
            @Param("actorId") UUID actorId,
            @Param("action") String action,
            @Param("targetType") String targetType,
            @Param("targetId") UUID targetId);
}
