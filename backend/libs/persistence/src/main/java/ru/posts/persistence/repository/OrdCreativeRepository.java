package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.OrdCreativeEntity;

public interface OrdCreativeRepository extends JpaRepository<OrdCreativeEntity, UUID> {

    List<OrdCreativeEntity> findByPostId(UUID postId);

    Optional<OrdCreativeEntity> findByWorkspaceIdAndProviderAndExternalId(
            UUID workspaceId, String provider, String externalId);

    Optional<OrdCreativeEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<OrdCreativeEntity> findByWorkspaceIdAndErid(UUID workspaceId, String erid);

    List<OrdCreativeEntity> findByWorkspaceIdAndProviderAndStatusOrderByUpdatedAtDesc(
            UUID workspaceId, String provider, String status);

    List<OrdCreativeEntity> findByWorkspaceIdOrderByUpdatedAtDesc(UUID workspaceId);

    @Query(
            """
            select c from OrdCreativeEntity c
            where c.workspaceId = :workspaceId
              and c.provider = :provider
              and c.status = 'registered'
              and c.erid is not null
              and (
                    c.finalStatsPending = true
                 or (
                      (c.displayStartedAt is null or c.displayStartedAt < :periodEnd)
                  and (c.displayEndedAt is null or c.displayEndedAt >= :periodStart)
                 )
              )
            """)
    List<OrdCreativeEntity> findVisibleInPeriod(
            @Param("workspaceId") UUID workspaceId,
            @Param("provider") String provider,
            @Param("periodStart") java.time.Instant periodStart,
            @Param("periodEnd") java.time.Instant periodEnd);

    @Query(
            """
            select c from OrdCreativeEntity c
            where c.status = 'registered'
              and c.displayEndedAt is null
              and c.expiryWarningSentAt is null
              and c.displayStartedAt is not null
              and c.autoDeleteAfterPeriod = true
            """)
    List<OrdCreativeEntity> findCandidatesForExpiryWarning();
}
