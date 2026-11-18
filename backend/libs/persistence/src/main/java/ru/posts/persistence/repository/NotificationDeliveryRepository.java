package ru.posts.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.NotificationDeliveryEntity;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDeliveryEntity, UUID> {

    Optional<NotificationDeliveryEntity> findByDedupeKeyAndChannel(String dedupeKey, String channel);

    boolean existsByDedupeKeyAndChannel(String dedupeKey, String channel);

    @Query(
            """
            SELECT d FROM NotificationDeliveryEntity d
            WHERE (d.status = 'failed' AND (d.nextAttemptAt IS NULL OR d.nextAttemptAt <= :now))
               OR (d.status = 'pending' AND d.nextAttemptAt IS NOT NULL AND d.nextAttemptAt <= :now)
            ORDER BY d.createdAt ASC
            """)
    List<NotificationDeliveryEntity> findDueDeliveries(@Param("now") Instant now, Pageable pageable);
}
