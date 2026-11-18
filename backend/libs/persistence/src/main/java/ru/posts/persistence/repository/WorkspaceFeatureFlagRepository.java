package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.WorkspaceFeatureFlagEntity;

public interface WorkspaceFeatureFlagRepository extends JpaRepository<WorkspaceFeatureFlagEntity, UUID> {
    List<WorkspaceFeatureFlagEntity> findByWorkspaceId(UUID workspaceId);

    Optional<WorkspaceFeatureFlagEntity> findByWorkspaceIdAndFlagKey(UUID workspaceId, String flagKey);

    List<WorkspaceFeatureFlagEntity> findByFlagKey(String flagKey);
}
