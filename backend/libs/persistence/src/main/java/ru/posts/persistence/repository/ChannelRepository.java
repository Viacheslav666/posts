package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.ChannelEntity;

public interface ChannelRepository extends JpaRepository<ChannelEntity, UUID> {
    List<ChannelEntity> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);

    Optional<ChannelEntity> findByWorkspaceIdAndNetworkAndExternalId(
            UUID workspaceId, String network, String externalId);
}
