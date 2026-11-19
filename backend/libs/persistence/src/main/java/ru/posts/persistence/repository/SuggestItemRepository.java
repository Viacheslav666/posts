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

    /**
     * Native search with explicit casts so PostgreSQL can type nullable filter params (avoids
     * {@code could not determine data type of parameter $N} / SQLGrammarException).
     */
    @Query(
            value =
                    """
                    SELECT i.* FROM suggest_items i
                    WHERE i.channel_id = :channelId
                      AND (CAST(:status AS text) IS NULL OR i.status = CAST(:status AS text))
                      AND (CAST(:authorVkId AS text) IS NULL OR i.author_vk_id = CAST(:authorVkId AS text))
                      AND (CAST(:fromTs AS timestamptz) IS NULL OR i.created_at >= CAST(:fromTs AS timestamptz))
                      AND (CAST(:toTs AS timestamptz) IS NULL OR i.created_at <= CAST(:toTs AS timestamptz))
                      AND (
                        CAST(:cursorCreatedAt AS timestamptz) IS NULL
                        OR i.created_at < CAST(:cursorCreatedAt AS timestamptz)
                        OR (
                          i.created_at = CAST(:cursorCreatedAt AS timestamptz)
                          AND i.id < CAST(:cursorId AS uuid)
                        )
                      )
                    ORDER BY i.created_at DESC, i.id DESC
                    """,
            nativeQuery = true)
    List<SuggestItemEntity> findByChannelCursor(
            @Param("channelId") UUID channelId,
            @Param("status") String status,
            @Param("authorVkId") String authorVkId,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);

    List<SuggestItemEntity> findByChannelIdAndStatusIn(UUID channelId, Collection<String> statuses);

    Optional<SuggestItemEntity> findFirstByChannelIdAndStatusInOrderByDecidedAtDesc(
            UUID channelId, Collection<String> statuses);

    List<SuggestItemEntity> findByStatusInAndRemoteActionIdIsNotNullOrderByUpdatedAtAsc(
            Collection<String> statuses, Pageable pageable);
}
