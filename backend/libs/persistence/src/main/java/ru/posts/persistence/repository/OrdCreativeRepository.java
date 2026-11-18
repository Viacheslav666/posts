package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.OrdCreativeEntity;

public interface OrdCreativeRepository extends JpaRepository<OrdCreativeEntity, UUID> {

    List<OrdCreativeEntity> findByPostId(UUID postId);

    Optional<OrdCreativeEntity> findByWorkspaceIdAndProviderAndExternalId(
            UUID workspaceId, String provider, String externalId);

    Optional<OrdCreativeEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
