package ru.posts.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.WorkspaceEntity;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {
    List<WorkspaceEntity> findByOwnerUserIdAndDeletedAtIsNull(UUID ownerUserId);

    Optional<WorkspaceEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<WorkspaceEntity> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkspaceEntity w where w.id = :id")
    Optional<WorkspaceEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            """
            select w from WorkspaceEntity w
            where w.deletedAt is null
              and (:q is null or lower(w.name) like lower(concat('%', cast(:q as string), '%')))
              and (:billingStatus is null or w.billingStatus = :billingStatus)
              and (:planId is null or w.planId = :planId)
              and (:ownerUserId is null or w.ownerUserId = :ownerUserId)
            order by w.createdAt desc, w.id desc
            """)
    List<WorkspaceEntity> search(
            @Param("q") String q,
            @Param("billingStatus") String billingStatus,
            @Param("planId") UUID planId,
            @Param("ownerUserId") UUID ownerUserId);
}
