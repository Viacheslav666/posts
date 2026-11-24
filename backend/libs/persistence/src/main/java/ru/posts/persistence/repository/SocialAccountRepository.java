package ru.posts.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.SocialAccountEntity;

public interface SocialAccountRepository extends JpaRepository<SocialAccountEntity, UUID> {
    List<SocialAccountEntity> findByWorkspaceId(UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SocialAccountEntity a where a.id = :id")
    Optional<SocialAccountEntity> findByIdForUpdate(@Param("id") UUID id);
}
