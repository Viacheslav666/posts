package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.PlanEntity;

public interface PlanRepository extends JpaRepository<PlanEntity, UUID> {
    Optional<PlanEntity> findByCode(String code);

    List<PlanEntity> findAllByOrderByPriceMonthAsc();

    List<PlanEntity> findAllByActiveTrueAndBillingModelOrderByPriceDayKopecksAsc(String billingModel);

    List<PlanEntity> findAllByActiveTrueOrderByPriceMonthAsc();
}
