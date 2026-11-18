package ru.posts.persistence.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.DailyChargeEntity;

public interface DailyChargeRepository extends JpaRepository<DailyChargeEntity, UUID> {

    Optional<DailyChargeEntity> findByWorkspaceIdAndChargeDate(UUID workspaceId, LocalDate chargeDate);

    boolean existsByWorkspaceIdAndChargeDate(UUID workspaceId, LocalDate chargeDate);

    Optional<DailyChargeEntity> findFirstByWorkspaceIdOrderByChargeDateDesc(UUID workspaceId);
}
