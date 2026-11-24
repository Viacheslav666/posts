package ru.posts.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.OutboxEntity;

public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {
    List<OutboxEntity> findByPublishedAtIsNullOrderByIdAsc();

    boolean existsByAggregateAndAggregateIdAndEventType(String aggregate, UUID aggregateId, String eventType);
}
