package ru.posts.api.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.persistence.entity.PromoCodeEntity;
import ru.posts.persistence.repository.PromoCodeRepository;
import ru.posts.persistence.repository.PromoRedemptionRepository;

@Component
@Profile("!openapi")
public class BillingPromoValidator {

    private final PromoCodeRepository promoCodeRepository;
    private final PromoRedemptionRepository promoRedemptionRepository;

    public BillingPromoValidator(
            PromoCodeRepository promoCodeRepository, PromoRedemptionRepository promoRedemptionRepository) {
        this.promoCodeRepository = promoCodeRepository;
        this.promoRedemptionRepository = promoRedemptionRepository;
    }

    public PromoCodeEntity requireValid(String code, UUID workspaceId) {
        PromoCodeEntity promo = promoCodeRepository
                .findByCodeIgnoreCase(code.trim())
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Promo code not found"));
        Instant now = Instant.now();
        if (!promo.isActive() || now.isBefore(promo.getValidFrom()) || now.isAfter(promo.getValidUntil())) {
            throw promoInvalid("Promo code is not active");
        }
        if (promo.getMaxRedemptions() != null && promo.getRedemptionCount() >= promo.getMaxRedemptions()) {
            throw promoInvalid("Promo code redemption limit reached");
        }
        if (promo.isOncePerWorkspace()
                && promoRedemptionRepository.existsByPromoCodeIdAndWorkspaceId(promo.getId(), workspaceId)) {
            throw promoInvalid("Promo code already used in this workspace");
        }
        return promo;
    }

    public BigDecimal applyDiscount(BigDecimal amount, PromoCodeEntity promo) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal result = amount;
        if ("percent".equals(promo.getKind())) {
            BigDecimal discount =
                    amount.multiply(promo.getValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            result = amount.subtract(discount);
        } else if ("fixed".equals(promo.getKind())) {
            result = amount.subtract(promo.getValue());
        }
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private static ApiException promoInvalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.PROMO_INVALID, message);
    }
}
