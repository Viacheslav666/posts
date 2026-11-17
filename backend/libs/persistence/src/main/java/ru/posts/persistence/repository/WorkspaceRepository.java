package ru.posts.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.WorkspaceEntity;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {
    List<WorkspaceEntity> findByOwnerUserIdAndDeletedAtIsNull(UUID ownerUserId);

    Optional<WorkspaceEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<WorkspaceEntity> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
