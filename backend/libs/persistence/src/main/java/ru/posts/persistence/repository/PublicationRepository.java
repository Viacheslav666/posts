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

    default Optional<PublicationEntity> findFirstByPublicationId(UUID id) {
        List<PublicationEntity> rows = findAllByPublicationId(id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }
}
