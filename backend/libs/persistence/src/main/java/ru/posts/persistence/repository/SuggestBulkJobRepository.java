package ru.posts.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.SuggestBulkJobEntity;

public interface SuggestBulkJobRepository extends JpaRepository<SuggestBulkJobEntity, UUID> {

    Optional<SuggestBulkJobEntity> findByWorkspaceIdAndIdempotencyKey(UUID workspaceId, String idempotencyKey);

    Optional<SuggestBulkJobEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
