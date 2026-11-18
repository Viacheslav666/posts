package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.OrdPlatformEntity;

public interface OrdPlatformRepository extends JpaRepository<OrdPlatformEntity, UUID> {

    List<OrdPlatformEntity> findByWorkspaceIdOrderByUpdatedAtDesc(UUID workspaceId);

    Optional<OrdPlatformEntity> findByWorkspaceIdAndProviderAndExternalId(
            UUID workspaceId, String provider, String externalId);
}
