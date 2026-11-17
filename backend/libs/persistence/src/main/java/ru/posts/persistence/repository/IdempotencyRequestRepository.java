package ru.posts.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.IdempotencyRequestEntity;
import ru.posts.persistence.entity.IdempotencyRequestEntity.IdempotencyRequestId;

public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequestEntity, IdempotencyRequestId> {}
