package ru.posts.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.NotificationUnsubscribeTokenEntity;

public interface NotificationUnsubscribeTokenRepository
        extends JpaRepository<NotificationUnsubscribeTokenEntity, UUID> {

    Optional<NotificationUnsubscribeTokenEntity> findByTokenHash(String tokenHash);
}
