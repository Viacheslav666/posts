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
}
