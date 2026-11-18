package ru.posts.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.AutomationRuleEntity;

public interface AutomationRuleRepository extends JpaRepository<AutomationRuleEntity, UUID> {

    Optional<AutomationRuleEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<AutomationRuleEntity> findByWorkspaceIdAndProjectIdAndTypeInOrderByCreatedAtDesc(
            UUID workspaceId, UUID projectId, Collection<String> types);

    List<AutomationRuleEntity> findByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(UUID workspaceId, UUID projectId);

    long countByWorkspaceIdAndTargetChannelIdAndStatusIn(
            UUID workspaceId, UUID targetChannelId, Collection<String> statuses);

    long countByWorkspaceIdAndTypeInAndStatusNot(UUID workspaceId, Collection<String> types, String status);
}
