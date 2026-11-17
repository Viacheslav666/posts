package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.ChannelEntity;

public interface ChannelRepository extends JpaRepository<ChannelEntity, UUID> {
    List<ChannelEntity> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);

    Optional<ChannelEntity> findByWorkspaceIdAndNetworkAndExternalId(
            UUID workspaceId, String network, String externalId);

    Optional<ChannelEntity> findByIdAndDeletedAtIsNull(UUID id);

    @Query(
            """
            select count(c) from ChannelEntity c
            where c.workspaceId = :workspaceId
              and c.deletedAt is null
              and c.status <> 'disabled'
            """)
    long countActiveByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    long countByBotIdAndDeletedAtIsNull(UUID botId);
}
