package ru.posts.api.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.ord.OrdDtos;
import ru.posts.api.web.dto.post.PostDtos;
import ru.posts.ord.api.AdMarkingDefaults;

class PostAdOptionsEnricherTest {

    @Test
    void fillsDeleteAfterFromPublishAtPlusDisplayPeriod() {
        Instant publishAt = Instant.parse("2026-06-01T10:00:00Z");
        OrdDtos.AdMarking marking = markingWithPeriod(AdMarkingDefaults.DEFAULT_DISPLAY_PERIOD_DAYS);
        var variant = new PostDtos.PostVariantUpsert(null, UUID.randomUUID(), ApiEnums.Network.vk, null, null);

        List<PostDtos.PostVariantUpsert> enriched =
                PostAdOptionsEnricher.enrichDeleteAfter(true, marking, publishAt, List.of(variant));

        Instant expected = publishAt.plusSeconds(
                AdMarkingDefaults.DEFAULT_DISPLAY_PERIOD_DAYS * AdMarkingDefaults.SECONDS_PER_DAY);
        assertThat(enriched.getFirst().options().deleteAfter()).isEqualTo(expected);
    }

    @Test
    void doesNotOverwriteExistingDeleteAfter() {
        Instant publishAt = Instant.parse("2026-06-01T10:00:00Z");
        Instant manual = Instant.parse("2026-07-01T10:00:00Z");
        OrdDtos.AdMarking marking = markingWithPeriod(30);
        var options = new PostDtos.PostVariantOptions(
                null, null, manual, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        var variant = new PostDtos.PostVariantUpsert(null, UUID.randomUUID(), ApiEnums.Network.vk, null, options);

        List<PostDtos.PostVariantUpsert> enriched =
                PostAdOptionsEnricher.enrichDeleteAfter(true, marking, publishAt, List.of(variant));

        assertThat(enriched.getFirst().options().deleteAfter()).isEqualTo(manual);
    }

    private static OrdDtos.AdMarking markingWithPeriod(int days) {
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
