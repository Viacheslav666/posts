package ru.posts.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.entity.PublicationId;

public interface PublicationRepository extends JpaRepository<PublicationEntity, PublicationId> {
    List<PublicationEntity> findByPostId(UUID postId);

    List<PublicationEntity> findByWorkspaceId(UUID workspaceId);
}
