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
import ru.posts.persistence.entity.SuggestItemEntity;

public interface SuggestItemRepository extends JpaRepository<SuggestItemEntity, UUID> {

    Optional<SuggestItemEntity> findByChannelIdAndVkPostId(UUID channelId, String vkPostId);

    Optional<SuggestItemEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<SuggestItemEntity> findBySettingsIdAndStatusOrderByCreatedAtAsc(UUID settingsId, String status);

    List<SuggestItemEntity> findBySettingsIdAndStatusInOrderByCreatedAtAsc(
            UUID settingsId, Collection<String> statuses);

    long countByChannelIdAndAuthorVkIdAndStatusInAndCreatedAtAfter(
            UUID channelId, String authorVkId, Collection<String> statuses, Instant after);

    long countByChannelIdAndStatusInAndCreatedAtAfter(UUID channelId, Collection<String> statuses, Instant after);

    @Query(
            """
            SELECT i FROM SuggestItemEntity i
            WHERE i.channelId = :channelId
              AND i.simhash IS NOT NULL
              AND i.status NOT IN :excluded
              AND i.createdAt >= :since
            ORDER BY i.createdAt DESC
            """)
    List<SuggestItemEntity> findRecentWithSimhashByChannel(
            @Param("channelId") UUID channelId,
            @Param("excluded") Collection<String> excluded,
            @Param("since") Instant since,
            Pageable pageable);

    @Query(
            """
            SELECT i FROM SuggestItemEntity i
            WHERE i.channelId = :channelId
              AND (:status IS NULL OR i.status = :status)
              AND (:authorVkId IS NULL OR i.authorVkId = :authorVkId)
              AND (:from IS NULL OR i.createdAt >= :from)
              AND (:to IS NULL OR i.createdAt <= :to)
              AND (:cursorCreatedAt IS NULL OR i.createdAt < :cursorCreatedAt
                   OR (i.createdAt = :cursorCreatedAt AND i.id < :cursorId))
            ORDER BY i.createdAt DESC, i.id DESC
            """)
    List<SuggestItemEntity> findByChannelCursor(
            @Param("channelId") UUID channelId,
            @Param("status") String status,
            @Param("authorVkId") String authorVkId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);

    List<SuggestItemEntity> findByChannelIdAndStatusIn(UUID channelId, Collection<String> statuses);

    Optional<SuggestItemEntity> findFirstByChannelIdAndStatusInOrderByDecidedAtDesc(
            UUID channelId, Collection<String> statuses);
}
