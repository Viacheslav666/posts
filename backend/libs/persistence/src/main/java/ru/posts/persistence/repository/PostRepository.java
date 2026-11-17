package ru.posts.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.PostEntity;

public interface PostRepository extends JpaRepository<PostEntity, UUID> {
    List<PostEntity> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);
}
