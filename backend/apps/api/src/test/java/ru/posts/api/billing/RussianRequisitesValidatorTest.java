package ru.posts.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.billing.BillingDtos;

class RussianRequisitesValidatorTest {

    private final RussianRequisitesValidator validator = new RussianRequisitesValidator();

    @Test
    @DisplayName("valid 10-digit INN with KPP accepted")
    void validLegalEntity() {
        // Known valid INN sample: 7707083893 (Sber)
        validator.validate(new BillingDtos.BuyerRequisites(
                "ООО Тест", "7707083893", "773601001", "1027700132195", "Москва", null, null, null));
    }

    @Test
    @DisplayName("invalid INN checksum rejected")
    void invalidInn() {
        assertThatThrownBy(() -> validator.validate(new BillingDtos.BuyerRequisites(
                        "ООО Тест", "7707083890", "773601001", null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ProblemCodes.BILLING_REQUISITES_INVALID);
    }

    @Test
    @DisplayName("checksum helper matches known INNs")
    void checksumHelper() {
        assertThat(RussianRequisitesValidator.isValidInn("7707083893")).isTrue();
        assertThat(RussianRequisitesValidator.isValidInn("500100732259")).isTrue(); // 12-digit valid sample
        assertThat(RussianRequisitesValidator.isValidInn("1234567890")).isFalse();
    }
}
