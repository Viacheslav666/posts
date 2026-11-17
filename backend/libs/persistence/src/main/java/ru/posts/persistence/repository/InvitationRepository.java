package ru.posts.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.InvitationEntity;

public interface InvitationRepository extends JpaRepository<InvitationEntity, UUID> {
    Optional<InvitationEntity> findByTokenHash(String tokenHash);

    java.util.List<InvitationEntity> findByWorkspaceId(UUID workspaceId);
}
