package ru.posts.api.ord;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.ord.OrdDtos;
import ru.posts.api.web.dto.post.PostDtos;

class AdMarkingValidatorTest {

    @Test
    void skipsWhenNotAd() {
        assertThatCode(() -> AdMarkingValidator.requireValidWhenAd(false, null)).doesNotThrowAnyException();
    }

    @Test
    void requiresMarkingWhenAd() {
        assertThatThrownBy(() -> AdMarkingValidator.requireValidWhenAd(true, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ProblemCodes.ORD_VALIDATION_FAILED);
    }

    @Test
    void rejectsInvalidInn() {
        OrdDtos.AdMarking marking = sample("123", "juridical");
        assertThatThrownBy(() -> AdMarkingValidator.requireValidWhenAd(true, marking))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ProblemCodes.AD_INN_INVALID);
    }

    @Test
    void rejectsBadChecksumInn() {
        // 10 digits but wrong checksum
        OrdDtos.AdMarking marking = sample("7707083890", "juridical");
        assertThatThrownBy(() -> AdMarkingValidator.requireValidWhenAd(true, marking))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ProblemCodes.AD_INN_INVALID);
    }

    @Test
    void acceptsValidMarking() {
        OrdDtos.AdMarking marking = sample("7707083893", "juridical");
        assertThatCode(() -> AdMarkingValidator.requireValidWhenAd(true, marking))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownKktuWhenDictionaryProvided() {
        OrdDtos.AdMarking marking = sample("7707083893", "juridical");
        assertThatThrownBy(() -> AdMarkingValidator.requireValidWhenAd(true, marking, Set.of("9.9.9"), Set.of("643")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ProblemCodes.ORD_VALIDATION_FAILED);
    }

    @Test
    void socialAdQuotaRequiresSocialAd() {
        OrdDtos.AdMarking marking = withSocial(sample("7707083893", "juridical"), false, true);
        assertThatThrownBy(() -> AdMarkingValidator.requireValidWhenAd(true, marking))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ProblemCodes.ORD_VALIDATION_FAILED);
    }

    @Test
    void donutOnlyOnVk() {
        PostDtos.PostVariantOptions options = optionsWithDonut(3);
        assertThatThrownBy(() -> AdMarkingValidator.validateNetworkOptions(ApiEnums.Network.tg, options))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ProblemCodes.NETWORK_OPTION_UNSUPPORTED);
        assertThatCode(() -> AdMarkingValidator.validateNetworkOptions(ApiEnums.Network.vk, options))
                .doesNotThrowAnyException();
    }

    @Test
    void coOwnersOnlyOnVk() {
        PostDtos.PostVariantOptions options = new PostDtos.PostVariantOptions(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("-123"),
                null,
                null,
                null,
                null,
                null,
                null);
        assertThatThrownBy(() -> AdMarkingValidator.validateNetworkOptions(ApiEnums.Network.ok, options))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ProblemCodes.NETWORK_OPTION_UNSUPPORTED);
    }

    private static OrdDtos.AdMarking sample(String inn, String orgType) {
        return sample(inn, orgType, false, false);
    }

    private static OrdDtos.AdMarking sample(String inn, String orgType, boolean socialAd, boolean socialAdQuota) {
        return new OrdDtos.AdMarking(
                "ООО Тест",
                inn,
                "770701001",
                orgType,
                BigDecimal.TEN,
                BigDecimal.TEN,
                LocalDate.of(2024, 1, 1),
                null,
                "Д-1",
                List.of("1.1.1"),
                "643",
                "without_vat",
                List.of("advertiser"),
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                "0",
                OrdDtos.MarkingPlacement.text_start,
                false,
                socialAd,
                socialAdQuota,
                "self",
                "advertiser",
                null,
                30,
                true,
                null,
                null,
                null,
                null,
                null);
    }

    private static OrdDtos.AdMarking withSocial(OrdDtos.AdMarking base, boolean socialAd, boolean socialAdQuota) {
        return new OrdDtos.AdMarking(
                base.advertiserName(),
                base.inn(),
                base.kpp(),
                base.orgType(),
                base.amount(),
                base.contractAmount(),
                base.contractDate(),
                base.contractDateEnd(),
                base.contractSerial(),
                base.kktuCodes(),
                base.oksmCountry(),
                base.vatRate(),
                base.roles(),
                base.isForeign(),
                base.foreignInn(),
                base.foreignRegNumber(),
                base.foreignEpayment(),
                base.phone(),
                base.address(),
                base.description(),
                base.markingType(),
                base.placement(),
                base.selfPromo(),
                socialAd,
                socialAdQuota,
                base.executor(),
                base.customerRole(),
                base.provider(),
                base.displayPeriodDays(),
                base.autoDeleteAfterPeriod(),
                base.contractorId(),
                base.erid(),
                base.amountVat(),
                base.contractAmountVat(),
                base.warnings());
    }

    private static PostDtos.PostVariantOptions optionsWithDonut(int days) {
        return new PostDtos.PostVariantOptions(
                null, null, null, null, null, null, null, null, null, days, null, null, null, null, null, null, null);
    }
}
