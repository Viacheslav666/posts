package ru.posts.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.GrabberItemEntity;

public interface GrabberItemRepository extends JpaRepository<GrabberItemEntity, UUID> {

    Optional<GrabberItemEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<GrabberItemEntity> findByRuleIdOrderByDetectedAtDesc(UUID ruleId);

    List<GrabberItemEntity> findByRuleIdAndStatusOrderByDetectedAtDesc(UUID ruleId, String status);

    List<GrabberItemEntity> findByWorkspaceIdAndIdIn(UUID workspaceId, Collection<UUID> ids);

    Optional<GrabberItemEntity> findByRuleIdAndSourceNetworkAndSourceIdAndRemotePostId(
            UUID ruleId, String sourceNetwork, String sourceId, String remotePostId);

    boolean existsByWorkspaceIdAndTargetChannelIdAndContentHashAndStatusNotIn(
            UUID workspaceId, UUID targetChannelId, String contentHash, Collection<String> statuses);

    long countByRuleIdAndStatus(UUID ruleId, String status);

    long countByRuleIdAndStatusAndDetectedAtAfter(UUID ruleId, String status, java.time.Instant after);
}
