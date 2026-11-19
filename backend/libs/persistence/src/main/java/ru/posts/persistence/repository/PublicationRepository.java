package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.entity.PublicationId;

public interface PublicationRepository extends JpaRepository<PublicationEntity, PublicationId> {
    List<PublicationEntity> findByPostId(UUID postId);

    List<PublicationEntity> findByWorkspaceId(UUID workspaceId);

    @Query("select p from PublicationEntity p where p.id = :id")
    List<PublicationEntity> findAllByPublicationId(@Param("id") UUID id);

    @Query(
            """
            select p from PublicationEntity p
            where p.workspaceId = :workspaceId
              and p.state in ('failed_permanent', 'failed_retryable')
            order by p.createdAt desc
            """)
    List<PublicationEntity> findRecentFailures(@Param("workspaceId") UUID workspaceId);

    @Query(
            value =
                    """
                    SELECT * FROM publications
                    WHERE channel_id = :channelId AND state = 'succeeded'
                    ORDER BY COALESCE(published_at, run_at) DESC
                    LIMIT 1
                    """,
            nativeQuery = true)
    List<PublicationEntity> findLatestSucceeded(@Param("channelId") UUID channelId);

    default Optional<PublicationEntity> findFirstByPublicationId(UUID id) {
        List<PublicationEntity> rows = findAllByPublicationId(id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }
}
