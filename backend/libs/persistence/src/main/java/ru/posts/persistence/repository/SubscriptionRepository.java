package ru.posts.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.SubscriptionEntity;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {
    List<SubscriptionEntity> findByWorkspaceId(UUID workspaceId);

    Optional<SubscriptionEntity> findFirstByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    List<SubscriptionEntity> findByStatusAndPeriodEndBefore(String status, Instant periodEnd);
}
