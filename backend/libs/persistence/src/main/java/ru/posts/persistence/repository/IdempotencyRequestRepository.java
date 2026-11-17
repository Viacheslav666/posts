package ru.posts.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.IdempotencyRequestEntity;
import ru.posts.persistence.entity.IdempotencyRequestEntity.IdempotencyRequestId;

public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequestEntity, IdempotencyRequestId> {

    Optional<IdempotencyRequestEntity> findByKeyAndUserId(String key, UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from IdempotencyRequestEntity e where e.key = :key and e.userId = :userId")
    int deleteByKeyAndUserId(@Param("key") String key, @Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from IdempotencyRequestEntity e where e.expiresAt is not null and e.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
