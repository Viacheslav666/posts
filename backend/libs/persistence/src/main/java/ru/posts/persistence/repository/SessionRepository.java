package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.SessionEntity;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {
    Optional<SessionEntity> findByRefreshTokenHashAndRevokedAtIsNull(String refreshTokenHash);

    List<SessionEntity> findByUserIdAndRevokedAtIsNull(UUID userId);
}
