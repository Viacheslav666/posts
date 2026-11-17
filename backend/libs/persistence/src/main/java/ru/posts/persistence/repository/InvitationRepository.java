package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.InvitationEntity;

public interface InvitationRepository extends JpaRepository<InvitationEntity, UUID> {
    Optional<InvitationEntity> findByTokenHash(String tokenHash);

    List<InvitationEntity> findByWorkspaceId(UUID workspaceId);

    @Query(
            """
            select i from InvitationEntity i
            where i.workspaceId = :workspaceId
              and i.acceptedAt is null
              and i.cancelledAt is null
            order by i.createdAt desc
            """)
    List<InvitationEntity> findPendingByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query(
            """
            select count(i) from InvitationEntity i
            where i.workspaceId = :workspaceId
              and i.acceptedAt is null
              and i.cancelledAt is null
            """)
    long countPendingByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
