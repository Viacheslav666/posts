package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.NotificationWebPushSubscriptionEntity;

public interface NotificationWebPushSubscriptionRepository
        extends JpaRepository<NotificationWebPushSubscriptionEntity, UUID> {

    List<NotificationWebPushSubscriptionEntity> findByUserId(UUID userId);

    Optional<NotificationWebPushSubscriptionEntity> findByUserIdAndEndpoint(UUID userId, String endpoint);

    void deleteByUserIdAndEndpoint(UUID userId, String endpoint);
}
