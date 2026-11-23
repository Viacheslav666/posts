package ru.posts.grabber.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.fetch.SourceCandidate;

/** BE-27 — tabular coverage for every skip-filter (positive + negative). */
class GrabberFiltersTest {

    @Test
    @DisplayName("TC-GRAB-19 exclude_ads and exclude_reposts")
    void excludesAdsAndReposts() {
        Map<String, Object> config = GrabberConfigSupport.withDefaults(Map.of());
        assertThat(GrabberFilters.skipReason(candidate("ad", true, false), config))
                .isEqualTo("exclude_ads");
        assertThat(GrabberFilters.skipReason(candidate("repost", false, true), config))
                .isEqualTo("exclude_reposts");
        assertThat(GrabberFilters.skipReason(candidate("ok", false, false), config))
                .isNull();
    }

    @Test
    @DisplayName("TC-GRAB-19 keyword include/exclude")
    void keywordFilters() {
        Map<String, Object> config = filters(Map.of(
                "exclude_ads",
                false,
                "exclude_reposts",
                false,
                "keyword_include",
                List.of("news"),
                "keyword_exclude",
                List.of("spam"),
                "keyword_match",
                "any"));
        assertThat(GrabberFilters.skipReason(candidate("breaking news today", false, false), config))
                .isNull();
        assertThat(GrabberFilters.skipReason(candidate("unrelated", false, false), config))
                .isEqualTo("keyword_include");
        assertThat(GrabberFilters.skipReason(candidate("news and spam", false, false), config))
                .isEqualTo("keyword_exclude");
    }

    @Test
    @DisplayName("stop_words by occurrence (Russian)")
    void stopWordsRussian() {
        Map<String, Object> config = filters(Map.of(
                "exclude_ads", false,
                "exclude_reposts", false,
                "stop_words", List.of("розыгрыш", "реклама")));
        assertThat(GrabberFilters.skipReason(candidate("Большой РОЗЫГРЫШ призов", false, false), config))
                .isEqualTo("stop_words");
        assertThat(GrabberFilters.skipReason(candidate("Обычная новость дня", false, false), config))
                .isNull();
    }

    @Nested
    class LinksAndText {
        @Test
        void excludeLinksYoulaAndVkCc() {
            Map<String, Object> config =
                    filters(Map.of("exclude_ads", false, "exclude_reposts", false, "exclude_links", true));
            assertThat(GrabberFilters.skipReason(candidate("см. https://youla.ru/item/1", false, false), config))
                    .isEqualTo("exclude_links");
            assertThat(GrabberFilters.skipReason(candidate("коротко vk.cc/abc", false, false), config))
                    .isEqualTo("exclude_links");
            assertThat(GrabberFilters.skipReason(candidate("просто текст", false, false), config))
                    .isNull();
        }

        @Test
        void skipIfNoTextAllowsEmojiOnlyAsBlank() {
            Map<String, Object> config =
                    filters(Map.of("exclude_ads", false, "exclude_reposts", false, "skip_if_no_text", true));
            assertThat(GrabberFilters.skipReason(candidate("   ", false, false), config))
                    .isEqualTo("skip_if_no_text");
            assertThat(GrabberFilters.skipReason(candidate("#tag @user", false, false), config))
                    .isEqualTo("skip_if_no_text");
            assertThat(GrabberFilters.skipReason(candidate("есть текст 🙂", false, false), config))
                    .isNull();
        }

        @Test
        void skipIfAnyText() {
            Map<String, Object> config =
                    filters(Map.of("exclude_ads", false, "exclude_reposts", false, "skip_if_any_text", true));
            assertThat(GrabberFilters.skipReason(candidate("текст", false, false), config))
                    .isEqualTo("skip_if_any_text");
            assertThat(GrabberFilters.skipReason(candidate("  ", false, false), config))
                    .isNull();
        }
    }

    @Nested
    class Attachments {
        @Test
        void skipIfNoMedia() {
            Map<String, Object> config =
                    filters(Map.of("exclude_ads", false, "exclude_reposts", false, "skip_if_no_media", true));
            assertThat(GrabberFilters.skipReason(withAtt("текст", List.of()), config))
                    .isEqualTo("skip_if_no_media");
            assertThat(GrabberFilters.skipReason(withAtt("текст", List.of(Map.of("type", "photo"))), config))
                    .isNull();
        }

        @ParameterizedTest
        @CsvSource({
            "skip_if_no_photo,photo",
            "skip_if_no_video,video",
            "skip_if_no_music,audio",
            "skip_if_no_poll,poll",
            "skip_if_no_file,doc",
            "skip_if_no_product,market",
            "skip_if_no_clip,clip"
        })
        void skipIfMissingSpecificType(String flag, String type) {
            Map<String, Object> f = new HashMap<>();
            f.put("exclude_ads", false);
            f.put("exclude_reposts", false);
            f.put(flag, true);
            Map<String, Object> config = filters(f);
            assertThat(GrabberFilters.skipReason(withAtt("x", List.of()), config))
                    .isEqualTo(flag);
            assertThat(GrabberFilters.skipReason(withAtt("x", List.of(Map.of("type", type))), config))
                    .isNull();
        }

        @ParameterizedTest
        @CsvSource({
            "skip_if_has_photo,photo",
            "skip_if_has_video,video",
            "skip_if_has_music,audio",
            "skip_if_has_poll,poll",
            "skip_if_has_file,doc",
            "skip_if_has_product,market",
            "skip_if_has_clip,clip",
            "skip_if_has_article,article"
        })
        void skipIfHasSpecificType(String flag, String type) {
            Map<String, Object> f = new HashMap<>();
            f.put("exclude_ads", false);
            f.put("exclude_reposts", false);
            f.put(flag, true);
            Map<String, Object> config = filters(f);
            assertThat(GrabberFilters.skipReason(withAtt("x", List.of(Map.of("type", type))), config))
                    .isEqualTo(flag);
            assertThat(GrabberFilters.skipReason(withAtt("x", List.of()), config))
                    .isNull();
        }
    }

    @Nested
    class HomoglyphsCapsPhone {
        @Test
        void homoglyphCyrillicLatinMix() {
            Map<String, Object> config =
                    filters(Map.of("exclude_ads", false, "exclude_reposts", false, "skip_if_homoglyphs", true));
            // Cyrillic Р + Latin e + Cyrillic к...
            assertThat(GrabberFilters.skipReason(candidate("Рeклама внутри", false, false), config))
                    .isEqualTo("skip_if_homoglyphs");
            assertThat(GrabberFilters.skipReason(candidate("Обычная реклама", false, false), config))
                    .isNull();
        }

        @Test
        void capsThreshold() {
            Map<String, Object> config = filters(Map.of(
                    "exclude_ads",
                    false,
                    "exclude_reposts",
                    false,
                    "skip_if_caps",
                    true,
                    "caps_threshold",
                    0.8,
                    "caps_min_letters",
                    5));
            assertThat(GrabberFilters.skipReason(candidate("СРОЧНО ВСЕМ ЧИТАТЬ", false, false), config))
                    .isEqualTo("skip_if_caps");
            assertThat(GrabberFilters.skipReason(candidate("Обычный заголовок новости", false, false), config))
                    .isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"Звоните +7 (999) 123-45-67", "тел 89991234567", "Contact +1 415 555 2671 now"})
        void skipIfPhone(String text) {
            Map<String, Object> config =
                    filters(Map.of("exclude_ads", false, "exclude_reposts", false, "skip_if_phone", true));
            assertThat(GrabberFilters.skipReason(candidate(text, false, false), config))
                    .isEqualTo("skip_if_phone");
        }

        @Test
        void requirePhone() {
            Map<String, Object> config =
                    filters(Map.of("exclude_ads", false, "exclude_reposts", false, "require_phone", true));
            assertThat(GrabberFilters.skipReason(candidate("просто текст", false, false), config))
                    .isEqualTo("require_phone");
            assertThat(GrabberFilters.skipReason(candidate("тел +79991234567", false, false), config))
                    .isNull();
        }

        @Test
        void requirePhoneOrSignature() {
            Map<String, Object> config =
                    filters(Map.of("exclude_ads", false, "exclude_reposts", false, "require_phone_or_signature", true));
            assertThat(GrabberFilters.skipReason(candidate("без контактов", false, false), config))
                    .isEqualTo("require_phone_or_signature");
            assertThat(GrabberFilters.skipReason(full("с подписью", false, false, null, true, true, List.of()), config))
                    .isNull();
            assertThat(GrabberFilters.skipReason(candidate("тел +79991234567", false, false), config))
                    .isNull();
        }

        @Test
        void onlyGroupPosts() {
            Map<String, Object> config =
                    filters(Map.of("exclude_ads", false, "exclude_reposts", false, "only_group_posts", true));
            assertThat(GrabberFilters.skipReason(full("user", false, false, null, false, false, List.of()), config))
                    .isEqualTo("only_group_posts");
            assertThat(GrabberFilters.skipReason(full("group", false, false, null, false, true, List.of()), config))
                    .isNull();
        }
    }

    @Test
    void maxAgeDays() {
        Instant now = Instant.parse("2026-11-18T12:00:00Z");
        Map<String, Object> config = filters(Map.of("exclude_ads", false, "exclude_reposts", false, "max_age_days", 3));
        assertThat(GrabberFilters.skipReason(
                        full("old", false, false, now.minus(5, ChronoUnit.DAYS), false, true, List.of()), config, now))
                .isEqualTo("max_age_days");
        assertThat(GrabberFilters.skipReason(
                        full("fresh", false, false, now.minus(1, ChronoUnit.DAYS), false, true, List.of()),
                        config,
                        now))
                .isNull();
    }

    @Test
    void workingWindowClosed() {
        Instant wednesdayNoonUtc = Instant.parse("2026-11-18T12:00:00Z"); // Wed
        Map<String, Object> config = GrabberConfigSupport.withDefaults(Map.of(
                "filters",
                Map.of("exclude_ads", false, "exclude_reposts", false),
                "schedule",
                Map.of(
                        "enabled",
                        true,
                        "timezone",
                        "UTC",
                        "days_of_week",
                        List.of(1, 2, 3, 4, 5),
                        "hours_from",
                        9,
                        "hours_to",
                        10)));
        assertThat(GrabberFilters.skipReason(candidate("x", false, false), config, wednesdayNoonUtc))
                .isEqualTo("outside_working_window");
    }

    private static Map<String, Object> filters(Map<String, Object> filters) {
        return GrabberConfigSupport.withDefaults(Map.of("filters", filters));
    }

    private static SourceCandidate candidate(String text, boolean ads, boolean repost) {
        return full(text, ads, repost, null, false, true, List.of());
    }

    private static SourceCandidate withAtt(String text, List<Map<String, Object>> atts) {
        return full(text, false, false, null, false, true, atts);
    }

    private static SourceCandidate full(
            String text,
            boolean ads,
            boolean repost,
            Instant publishedAt,
            boolean signed,
            boolean fromGroup,
            List<Map<String, Object>> atts) {
        return new SourceCandidate(
                "vk",
                "-1",
                "wall-1_1",
                "https://vk.com/wall-1_1",
                "src",
                text,
                null,
                atts,
                List.of(),
                List.of(),
                ads,
                repost,
                publishedAt,
                signed,
                fromGroup,
                Map.of());
    }
}
