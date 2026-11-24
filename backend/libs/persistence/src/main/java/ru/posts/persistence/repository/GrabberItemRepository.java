package ru.posts.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.GrabberItemEntity;

public interface GrabberItemRepository extends JpaRepository<GrabberItemEntity, UUID> {

    Optional<GrabberItemEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /** Row lock for approve/retry so two concurrent publishes cannot both insert a post. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from GrabberItemEntity i where i.id = :id")
    Optional<GrabberItemEntity> findByIdForUpdate(@Param("id") UUID id);

    List<GrabberItemEntity> findByRuleIdOrderByDetectedAtDesc(UUID ruleId);

    List<GrabberItemEntity> findByRuleIdAndStatusOrderByDetectedAtDesc(UUID ruleId, String status);

    List<GrabberItemEntity> findByWorkspaceIdAndIdIn(UUID workspaceId, Collection<UUID> ids);

    Optional<GrabberItemEntity> findByRuleIdAndSourceNetworkAndSourceIdAndRemotePostId(
            UUID ruleId, String sourceNetwork, String sourceId, String remotePostId);

    boolean existsByWorkspaceIdAndTargetChannelIdAndContentHashAndStatusNotIn(
            UUID workspaceId, UUID targetChannelId, String contentHash, Collection<String> statuses);

    long countByRuleIdAndStatus(UUID ruleId, String status);

    long countByRuleIdAndStatusAndDetectedAtAfter(UUID ruleId, String status, Instant after);

    long countByRuleIdAndSourceIdAndStatusInAndDetectedAtAfter(
            UUID ruleId, String sourceId, Collection<String> statuses, Instant after);

    long countByRuleIdAndStatusInAndDetectedAtAfter(UUID ruleId, Collection<String> statuses, Instant after);

    Optional<GrabberItemEntity> findFirstByRuleIdAndStatusInOrderByDetectedAtDesc(
            UUID ruleId, Collection<String> statuses);

    @Query(
            """
            SELECT i FROM GrabberItemEntity i
            WHERE i.ruleId = :ruleId
              AND i.simhash IS NOT NULL
              AND i.status NOT IN :excluded
            ORDER BY i.createdAt DESC
            """)
    List<GrabberItemEntity> findRecentWithSimhashByRule(
            @Param("ruleId") UUID ruleId, @Param("excluded") Collection<String> excluded, Pageable pageable);

    @Query(
            """
            SELECT i FROM GrabberItemEntity i
            WHERE i.workspaceId = :workspaceId
              AND i.targetChannelId = :channelId
              AND i.simhash IS NOT NULL
              AND i.status NOT IN :excluded
            ORDER BY i.createdAt DESC
            """)
    List<GrabberItemEntity> findRecentWithSimhashByChannel(
            @Param("workspaceId") UUID workspaceId,
            @Param("channelId") UUID channelId,
            @Param("excluded") Collection<String> excluded,
            Pageable pageable);
}
