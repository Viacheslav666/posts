package ru.posts.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.CrosspostRuleEntity;

public interface CrosspostRuleRepository extends JpaRepository<CrosspostRuleEntity, UUID> {

    Optional<CrosspostRuleEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<CrosspostRuleEntity> findByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(UUID workspaceId, UUID projectId);

    List<CrosspostRuleEntity> findBySourceChannelIdAndStatus(UUID sourceChannelId, String status);

    long countByWorkspaceIdAndStatusNot(UUID workspaceId, String status);

    long countByWorkspaceIdAndStatusIn(UUID workspaceId, Collection<String> statuses);
}
