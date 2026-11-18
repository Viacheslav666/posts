package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.PaymentEntity;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByProviderPaymentId(String providerPaymentId);

    Optional<PaymentEntity> findByIdempotenceKey(String idempotenceKey);

    List<PaymentEntity> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
}
