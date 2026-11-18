package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.BillingWebhookInboxEntity;

public interface BillingWebhookInboxRepository extends JpaRepository<BillingWebhookInboxEntity, UUID> {
    Optional<BillingWebhookInboxEntity> findByEventId(String eventId);

    List<BillingWebhookInboxEntity> findByStatusOrderByReceivedAtAsc(String status);
}
