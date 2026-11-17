package ru.posts.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.SocialAccountEntity;

public interface SocialAccountRepository extends JpaRepository<SocialAccountEntity, UUID> {
    List<SocialAccountEntity> findByWorkspaceId(UUID workspaceId);
}
