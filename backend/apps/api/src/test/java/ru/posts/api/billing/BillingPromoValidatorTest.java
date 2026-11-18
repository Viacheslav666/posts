package ru.posts.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.PromoCodeEntity;
import ru.posts.persistence.repository.PromoCodeRepository;
import ru.posts.persistence.repository.PromoRedemptionRepository;

@ExtendWith(MockitoExtension.class)
class BillingPromoValidatorTest {

    @Mock
    PromoCodeRepository promoCodeRepository;

    @Mock
    PromoRedemptionRepository promoRedemptionRepository;

    BillingPromoValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BillingPromoValidator(promoCodeRepository, promoRedemptionRepository);
    }

    @Test
    void percentDiscountApplied() {
        PromoCodeEntity promo = activePromo("percent", new BigDecimal("10"));
        BigDecimal discounted = validator.applyDiscount(new BigDecimal("990.00"), promo);
        assertThat(discounted).isEqualByComparingTo("891.00");
    }

    @Test
    void fixedDiscountFloorAtZero() {
        PromoCodeEntity promo = activePromo("fixed", new BigDecimal("5000"));
        BigDecimal discounted = validator.applyDiscount(new BigDecimal("390.00"), promo);
        assertThat(discounted).isEqualByComparingTo("0.00");
    }

    @Test
    void inactivePromoRejected() {
        UUID workspaceId = UuidV7.generate();
        PromoCodeEntity promo = activePromo("percent", new BigDecimal("10"));
        promo.setActive(false);
        when(promoCodeRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> validator.requireValid("SAVE10", workspaceId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ProblemCodes.PROMO_INVALID));
    }

    @Test
    void oncePerWorkspaceBlocksSecondUse() {
        UUID workspaceId = UuidV7.generate();
        PromoCodeEntity promo = activePromo("percent", new BigDecimal("10"));
        when(promoCodeRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(promo));
        when(promoRedemptionRepository.existsByPromoCodeIdAndWorkspaceId(promo.getId(), workspaceId))
                .thenReturn(true);

        assertThatThrownBy(() -> validator.requireValid("SAVE10", workspaceId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ProblemCodes.PROMO_INVALID));
    }

    private static PromoCodeEntity activePromo(String kind, BigDecimal value) {
        PromoCodeEntity promo = new PromoCodeEntity();
        promo.setId(UuidV7.generate());
        promo.setCode("SAVE10");
        promo.setKind(kind);
        promo.setValue(value);
        promo.setActive(true);
        promo.setOncePerWorkspace(true);
        promo.setValidFrom(Instant.now().minus(1, ChronoUnit.DAYS));
        promo.setValidUntil(Instant.now().plus(30, ChronoUnit.DAYS));
        promo.setRedemptionCount(0);
        return promo;
    }
}
