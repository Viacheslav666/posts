package ru.posts.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.PromoRedemptionEntity;

public interface PromoRedemptionRepository extends JpaRepository<PromoRedemptionEntity, UUID> {
    boolean existsByPromoCodeIdAndWorkspaceId(UUID promoCodeId, UUID workspaceId);

    long countByPromoCodeId(UUID promoCodeId);
}
