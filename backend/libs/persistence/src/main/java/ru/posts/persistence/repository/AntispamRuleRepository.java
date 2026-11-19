package ru.posts.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.AntispamRuleEntity;

public interface AntispamRuleRepository extends JpaRepository<AntispamRuleEntity, UUID> {

    Optional<AntispamRuleEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<AntispamRuleEntity> findByWorkspaceIdAndProjectIdOrderByPriorityAscCreatedAtDesc(
            UUID workspaceId, UUID projectId);

    List<AntispamRuleEntity> findByChannelIdAndStatusOrderByPriorityAsc(UUID channelId, String status);

    long countByWorkspaceIdAndStatusNot(UUID workspaceId, String status);

    long countByWorkspaceIdAndChannelIdAndStatusIn(UUID workspaceId, UUID channelId, Collection<String> statuses);
}
