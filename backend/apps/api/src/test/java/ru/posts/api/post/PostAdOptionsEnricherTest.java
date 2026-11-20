package ru.posts.api.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.posts.api.web.dto.ord.OrdDtos;
import ru.posts.ord.api.AdMarkingDefaults;

class PostAdOptionsEnricherTest {

    @Test
    void resolveDefaultUsesDisplayPeriodFromMarking() {
        Instant publishAt = Instant.parse("2026-06-01T10:00:00Z");
        OrdDtos.AdMarking marking = markingWithPeriod(14);
        Instant result = PostAdOptionsEnricher.resolveDefaultDeleteAfter(marking, publishAt);
        assertThat(result).isEqualTo(publishAt.plusSeconds(14 * AdMarkingDefaults.SECONDS_PER_DAY));
    }

    @Test
    void resolveDefaultFallsBackTo30WhenDisplayPeriodMissingOnMarking() {
        Instant publishAt = Instant.parse("2026-06-01T10:00:00Z");
        OrdDtos.AdMarking marking = markingWithPeriod(null);
        Instant result = PostAdOptionsEnricher.resolveDefaultDeleteAfter(marking, publishAt);
        assertThat(result)
                .isEqualTo(publishAt.plusSeconds(
                        AdMarkingDefaults.DEFAULT_DISPLAY_PERIOD_DAYS * AdMarkingDefaults.SECONDS_PER_DAY));
    }

    @Test
    void resolveDefaultNullWhenAutoDeleteDisabled() {
        OrdDtos.AdMarking marking = markingWithPeriod(30);
        OrdDtos.AdMarking off = new OrdDtos.AdMarking(
                marking.advertiserName(),
                marking.inn(),
                marking.kpp(),
                marking.orgType(),
                marking.amount(),
                marking.contractAmount(),
                marking.contractDate(),
                marking.contractDateEnd(),
                marking.contractSerial(),
                marking.kktuCodes(),
                marking.oksmCountry(),
                marking.vatRate(),
                marking.roles(),
                marking.isForeign(),
                marking.foreignInn(),
                marking.foreignRegNumber(),
                marking.foreignEpayment(),
                marking.phone(),
                marking.address(),
                marking.description(),
                marking.markingType(),
                marking.placement(),
                marking.selfPromo(),
                marking.socialAd(),
                marking.socialAdQuota(),
                marking.executor(),
                marking.customerRole(),
                marking.provider(),
                marking.displayPeriodDays(),
                false,
                marking.contractorId(),
                marking.erid(),
                marking.amountVat(),
                marking.contractAmountVat(),
                marking.warnings());
        assertThat(PostAdOptionsEnricher.resolveDefaultDeleteAfter(off, Instant.now()))
                .isNull();
    }

    private static OrdDtos.AdMarking markingWithPeriod(Integer days) {
        return new OrdDtos.AdMarking(
                "ООО Тест",
                "7707083893",
                "770701001",
                "juridical",
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
                false,
                false,
                "self",
                "advertiser",
                null,
                days,
                true,
                null,
                null,
                null,
                null,
                null);
    }
}
