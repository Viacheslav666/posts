package ru.posts.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.AdminIdempotencyEntity;

public interface AdminIdempotencyRepository extends JpaRepository<AdminIdempotencyEntity, UUID> {
    Optional<AdminIdempotencyEntity> findByScopeAndIdempotencyKey(String scope, String idempotencyKey);
}
