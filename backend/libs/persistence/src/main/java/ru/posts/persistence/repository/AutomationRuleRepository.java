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
import ru.posts.persistence.entity.AutomationRuleEntity;

public interface AutomationRuleRepository extends JpaRepository<AutomationRuleEntity, UUID> {

    Optional<AutomationRuleEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /** Serializes overlapping runs of one rule so both cannot pass the daily quota. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AutomationRuleEntity r WHERE r.id = :id")
    Optional<AutomationRuleEntity> findByIdForUpdate(@Param("id") UUID id);

    List<AutomationRuleEntity> findByWorkspaceIdAndProjectIdAndTypeInOrderByCreatedAtDesc(
            UUID workspaceId, UUID projectId, Collection<String> types);

    List<AutomationRuleEntity> findByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(UUID workspaceId, UUID projectId);

    long countByWorkspaceIdAndTargetChannelIdAndStatusIn(
            UUID workspaceId, UUID targetChannelId, Collection<String> statuses);

    long countByWorkspaceIdAndTypeInAndStatusNot(UUID workspaceId, Collection<String> types, String status);
}
