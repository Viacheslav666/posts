package ru.posts.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.CrosspostItemEntity;

public interface CrosspostItemRepository extends JpaRepository<CrosspostItemEntity, UUID> {

    Optional<CrosspostItemEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<CrosspostItemEntity> findByRuleIdAndSourceRemoteId(UUID ruleId, String sourceRemoteId);

    List<CrosspostItemEntity> findByRuleIdOrderByCreatedAtDesc(UUID ruleId, Pageable pageable);

    List<CrosspostItemEntity> findByRuleIdAndStatusOrderByCreatedAtDesc(UUID ruleId, String status, Pageable pageable);

    @Query(
            value =
                    """
                    SELECT * FROM crosspost_items
                    WHERE rule_id = :ruleId
                      AND (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
                    ORDER BY created_at DESC, id DESC
                    LIMIT :limit OFFSET :offset
                    """,
            nativeQuery = true)
    List<CrosspostItemEntity> pageByRule(
            @Param("ruleId") UUID ruleId,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset);

    List<CrosspostItemEntity> findByStatusInOrderByUpdatedAtAsc(Collection<String> statuses, Pageable pageable);

    @Query(
            """
            SELECT i FROM CrosspostItemEntity i
            WHERE i.status = 'received'
              AND i.processAfter <= :now
            ORDER BY i.processAfter ASC
            """)
    List<CrosspostItemEntity> findDueReceived(@Param("now") Instant now, Pageable pageable);

    long countByRuleIdAndCreatedAtAfter(UUID ruleId, Instant after);

    long countByRuleIdAndStatusAndCreatedAtAfter(UUID ruleId, String status, Instant after);

    Optional<CrosspostItemEntity> findFirstByRuleIdOrderByCreatedAtDesc(UUID ruleId);

    Optional<CrosspostItemEntity> findByPostId(UUID postId);

    @Query(
            """
            SELECT i FROM CrosspostItemEntity i
            WHERE i.workspaceId = :workspaceId
              AND i.simhash IS NOT NULL
              AND i.status IN :statuses
              AND i.createdAt >= :since
            ORDER BY i.createdAt DESC
            """)
    List<CrosspostItemEntity> findRecentByWorkspaceAndStatuses(
            @Param("workspaceId") UUID workspaceId,
            @Param("statuses") Collection<String> statuses,
            @Param("since") Instant since,
            Pageable pageable);
}
