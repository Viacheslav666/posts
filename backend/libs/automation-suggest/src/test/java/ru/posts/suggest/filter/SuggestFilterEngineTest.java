package ru.posts.suggest.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.posts.suggest.config.SuggestConfigSupport;

/** BE-34 — filter engine coverage (≥30 cases): keywords, authors, limits, schedule, ads. */
class SuggestFilterEngineTest {

    @Test
    @DisplayName("ё/е normalization matches include keyword")
    void yoToYeNormalization() {
        Map<String, Object> config = filters(Map.of("keyword_include", List.of("зеленый")));
        assertThat(code(item("Ёлка зелёный сад"), config)).isNull();
        assertThat(code(item("без слова"), config)).isEqualTo("keyword_include");
    }

    @Test
    @DisplayName("case insensitivity for keywords")
    void caseInsensitiveKeywords() {
        Map<String, Object> config = filters(Map.of("keyword_include", List.of("НоВоСтЬ")));
        assertThat(code(item("СРОЧНАЯ новость дня"), config)).isNull();
    }

    @Test
    @DisplayName("substring vs whole_word")
    void substringVersusWholeWord() {
        Map<String, Object> substring =
                filters(Map.of("keyword_include", List.of("кот"), "keyword_match_mode", "substring"));
        assertThat(code(item("скотчина"), substring)).isNull();

        Map<String, Object> whole =
                filters(Map.of("keyword_include", List.of("кот"), "keyword_match_mode", "whole_word"));
        assertThat(code(item("скотчина"), whole)).isEqualTo("keyword_include");
        assertThat(code(item("мой кот спит"), whole)).isNull();
    }

    @Test
    @DisplayName("whole_word does not match inside longer token")
    void wholeWordBoundaries() {
        Map<String, Object> config = filters(Map.of("stop_words", List.of("spam"), "keyword_match_mode", "whole_word"));
        assertThat(code(item("spammer alert"), config)).isNull();
        assertThat(code(item("this is spam today"), config)).isEqualTo("stop_word");
    }

    @Test
    @DisplayName("emoji in text still allows keyword match")
    void emojiInText() {
        Map<String, Object> config = filters(Map.of("keyword_include", List.of("акция")));
        assertThat(code(item("🔥 Акция недели 🙂"), config)).isNull();
    }

    @Test
    @DisplayName("emoji-only text passes when skip_if_no_text is off")
    void emojiOnlyWithoutSkip() {
        Map<String, Object> config = SuggestConfigSupport.withDefaults(Map.of());
        assertThat(code(item("🙂🔥"), config)).isNull();
    }

    @Test
    @DisplayName("skip_if_no_text")
    void skipIfNoText() {
        Map<String, Object> config = filters(Map.of("skip_if_no_text", true));
        assertThat(code(item("   "), config)).isEqualTo("skip_if_no_text");
        assertThat(code(item("есть текст"), config)).isNull();
    }

    @Test
    @DisplayName("skip_if_no_image")
    void skipIfNoImage() {
        Map<String, Object> config = filters(Map.of("skip_if_no_image", true));
        assertThat(code(item("текст"), config)).isEqualTo("skip_if_no_image");
        assertThat(code(withAtt("текст", List.of(Map.of("type", "photo"))), config))
                .isNull();
    }

    @Test
    @DisplayName("skip_if_has_attachment_types")
    void skipIfHasAttachmentTypes() {
        Map<String, Object> config = filters(Map.of("skip_if_has_attachment_types", List.of("video", "link")));
        assertThat(code(withAtt("x", List.of(Map.of("type", "video"))), config))
                .isEqualTo("skip_if_has_attachment_types");
        assertThat(code(withAtt("x", List.of(Map.of("type", "photo"))), config)).isNull();
    }

    @Test
    @DisplayName("exclude_links in text and attachments")
    void excludeLinks() {
        Map<String, Object> config = filters(Map.of("exclude_links", true));
        assertThat(code(item("см. https://youla.ru/item/1"), config)).isEqualTo("exclude_links");
        assertThat(code(item("коротко vk.cc/abc"), config)).isEqualTo("exclude_links");
        assertThat(code(withAtt("текст", List.of(Map.of("type", "link"))), config))
                .isEqualTo("exclude_links");
        assertThat(code(item("просто текст"), config)).isNull();
    }

    @Test
    @DisplayName("author whitelist skips other filters")
    void authorWhitelist() {
        Map<String, Object> config = filters(Map.of(
                "author_whitelist", List.of("100"),
                "stop_words", List.of("спам"),
                "skip_if_no_text", true));
        SuggestDecision decision = evaluate(new SuggestFilterItem("спам", "100", List.of(), null), config, ctx());
        assertThat(decision.action()).isEqualTo(SuggestAction.PUBLISH);
        assertThat(decision.primaryCode()).isEqualTo("author_whitelist");
    }

    @Test
    @DisplayName("author blacklist → REJECT")
    void authorBlacklist() {
        Map<String, Object> config = filters(Map.of("author_blacklist", List.of("42")));
        SuggestDecision decision = evaluate(item("ok", "42"), config, ctx());
        assertThat(decision.action()).isEqualTo(SuggestAction.REJECT);
        assertThat(decision.primaryCode()).isEqualTo("author_blacklist");
    }

    @Test
    @DisplayName("closed profile → REJECT")
    void closedProfile() {
        Map<String, Object> config = filters(Map.of("reject_closed_profile", true));
        SuggestFilterContext closed = SuggestFilterContext.builder()
                .now(Instant.parse("2026-11-18T12:00:00Z"))
                .authorProfileClosed(true)
                .build();
        assertThat(evaluate(item("ok"), config, closed).primaryCode()).isEqualTo("closed_profile");
        SuggestFilterContext open = SuggestFilterContext.builder()
                .now(Instant.parse("2026-11-18T12:00:00Z"))
                .authorProfileClosed(false)
                .build();
        assertThat(evaluate(item("ok"), config, open).passed()).isTrue();
    }

    @Test
    @DisplayName("require_messages_allowed → REJECT no_dialog_with_community")
    void requireMessagesAllowed() {
        Map<String, Object> config = filters(Map.of("require_messages_allowed", true));
        SuggestFilterContext denied = SuggestFilterContext.builder()
                .now(Instant.parse("2026-11-18T12:00:00Z"))
                .authorMessagesAllowed(false)
                .build();
        SuggestDecision decision = evaluate(item("ok"), config, denied);
        assertThat(decision.action()).isEqualTo(SuggestAction.REJECT);
        assertThat(decision.primaryCode()).isEqualTo("no_dialog_with_community");
    }

    @Test
    @DisplayName("author day limit: 3rd post filtered out")
    void authorDayLimitThirdPost() {
        Map<String, Object> config = configWith(Map.of("limits", Map.of("author_per_day", 2)));
        SuggestFilterContext twoAlready = SuggestFilterContext.builder()
                .now(Instant.parse("2026-11-18T12:00:00Z"))
                .authorPublishedLastDay(2)
                .build();
        SuggestDecision decision = evaluate(item("ok"), config, twoAlready);
        assertThat(decision.action()).isEqualTo(SuggestAction.FILTER_OUT);
        assertThat(decision.primaryCode()).isEqualTo("author_limit_day");
    }

    @Test
    @DisplayName("channel hour limit")
    void channelHourLimit() {
        Map<String, Object> config = configWith(Map.of("limits", Map.of("channel_per_hour", 1)));
        SuggestFilterContext ctx = SuggestFilterContext.builder()
                .now(Instant.parse("2026-11-18T12:00:00Z"))
                .publishedLastHour(1)
                .build();
        assertThat(evaluate(item("ok"), config, ctx).primaryCode()).isEqualTo("channel_limit_hour");
    }

    @Test
    @DisplayName("stop words → REJECT")
    void stopWordsReject() {
        Map<String, Object> config = filters(Map.of("stop_words", List.of("розыгрыш", "реклама")));
        SuggestDecision decision = evaluate(item("Большой РОЗЫГРЫШ призов"), config, ctx());
        assertThat(decision.action()).isEqualTo(SuggestAction.REJECT);
        assertThat(decision.primaryCode()).isEqualTo("stop_word");
        assertThat(evaluate(item("Обычная новость"), config, ctx()).passed()).isTrue();
    }

    @Test
    @DisplayName("keyword_exclude → REJECT")
    void keywordExcludeReject() {
        Map<String, Object> config = filters(Map.of("keyword_exclude", List.of("казино")));
        SuggestDecision decision = evaluate(item("Лучшее казино онлайн"), config, ctx());
        assertThat(decision.action()).isEqualTo(SuggestAction.REJECT);
        assertThat(decision.primaryCode()).isEqualTo("keyword_exclude");
    }

    @Test
    @DisplayName("missing keyword_include → FILTER_OUT")
    void keywordIncludeFilterOut() {
        Map<String, Object> config = filters(Map.of("keyword_include", List.of("вакансия")));
        SuggestDecision decision = evaluate(item("просто текст"), config, ctx());
        assertThat(decision.action()).isEqualTo(SuggestAction.FILTER_OUT);
        assertThat(decision.primaryCode()).isEqualTo("keyword_include");
    }

    @Test
    @DisplayName("near duplicate → FILTER_OUT")
    void nearDuplicate() {
        Map<String, Object> config = SuggestConfigSupport.withDefaults(Map.of());
        SuggestFilterContext dup = SuggestFilterContext.builder()
                .now(Instant.parse("2026-11-18T12:00:00Z"))
                .nearDuplicate(true)
                .build();
        SuggestDecision decision = evaluate(item("повтор"), config, dup);
        assertThat(decision.action()).isEqualTo(SuggestAction.FILTER_OUT);
        assertThat(decision.primaryCode()).isEqualTo("near_duplicate");
    }

    @Test
    @DisplayName("pause_after_ads when adsPauseActive")
    void pauseAfterAds() {
        Map<String, Object> config = SuggestConfigSupport.withDefaults(Map.of());
        SuggestFilterContext paused = SuggestFilterContext.builder()
                .now(Instant.parse("2026-11-18T12:00:00Z"))
                .lastWallIsAds(true)
                .adsPauseActive(true)
                .build();
        assertThat(evaluate(item("ok"), config, paused).primaryCode()).isEqualTo("pause_after_ads");

        SuggestFilterContext flagOnly = SuggestFilterContext.builder()
                .now(Instant.parse("2026-11-18T12:00:00Z"))
                .lastWallIsAds(true)
                .adsPauseActive(false)
                .build();
        assertThat(evaluate(item("ok"), config, flagOnly).passed()).isTrue();
    }

    @Test
    @DisplayName("schedule.enabled=false always open")
    void scheduleDisabledAlwaysOpen() {
        Map<String, Object> config = SuggestConfigSupport.withDefaults(Map.of());
        Instant night = Instant.parse("2026-11-18T03:00:00Z");
        assertThat(evaluate(item("ok"), config, SuggestFilterContext.empty(night))
                        .passed())
                .isTrue();
    }

    @Nested
    @DisplayName("schedule windows at midnight boundary")
    class ScheduleMidnight {

        @Test
        void moscowExactlyMidnightIsOpen() {
            // 2026-11-18 Wednesday; 21:00 UTC = 00:00 Europe/Moscow
            Instant midnightMsk = Instant.parse("2026-11-18T21:00:00Z");
            Map<String, Object> config = scheduleConfig(
                    "Europe/Moscow", List.of(Map.of("days", List.of(4), "from", "00:00", "to", "09:00")));
            SuggestFilterContext ctx = SuggestFilterContext.builder()
                    .now(midnightMsk)
                    .zone("Europe/Moscow")
                    .build();
            assertThat(evaluate(item("ok"), config, ctx).passed()).isTrue();
        }

        @Test
        void moscowJustBeforeMidnightClosed() {
            // 20:59 UTC = 23:59 Europe/Moscow Wednesday (day 3)
            Instant before = Instant.parse("2026-11-18T20:59:00Z");
            Map<String, Object> config = scheduleConfig(
                    "Europe/Moscow", List.of(Map.of("days", List.of(4), "from", "00:00", "to", "09:00")));
            SuggestFilterContext ctx = SuggestFilterContext.builder()
                    .now(before)
                    .zone("Europe/Moscow")
                    .build();
            SuggestDecision decision = evaluate(item("ok"), config, ctx);
            assertThat(decision.action()).isEqualTo(SuggestAction.FILTER_OUT);
            assertThat(decision.primaryCode()).isEqualTo("outside_schedule");
        }

        @Test
        void samaraMidnightBoundary() {
            // Europe/Samara = UTC+4; 20:00 UTC = 00:00 Samara
            Instant midnightSamara = Instant.parse("2026-11-18T20:00:00Z");
            Map<String, Object> config = scheduleConfig(
                    "Europe/Samara", List.of(Map.of("days", List.of(4), "from", "00:00", "to", "01:00")));
            SuggestFilterContext open = SuggestFilterContext.builder()
                    .now(midnightSamara)
                    .zone("Europe/Samara")
                    .build();
            assertThat(evaluate(item("ok"), config, open).passed()).isTrue();

            Instant closed = Instant.parse("2026-11-18T19:59:00Z"); // 23:59 Samara Wed
            SuggestFilterContext ctx = SuggestFilterContext.builder()
                    .now(closed)
                    .zone("Europe/Samara")
                    .build();
            assertThat(evaluate(item("ok"), config, ctx).primaryCode()).isEqualTo("outside_schedule");
        }

        @Test
        void moscowVsSamaraSameInstant() {
            // Instant 21:00 UTC: Moscow 00:00 Thu, Samara 01:00 Thu
            Instant instant = Instant.parse("2026-11-18T21:00:00Z");
            Map<String, Object> msk = scheduleConfig(
                    "Europe/Moscow", List.of(Map.of("days", List.of(4), "from", "00:00", "to", "00:30")));
            Map<String, Object> samara = scheduleConfig(
                    "Europe/Samara", List.of(Map.of("days", List.of(4), "from", "00:00", "to", "00:30")));
            assertThat(evaluate(
                                    item("ok"),
                                    msk,
                                    SuggestFilterContext.builder()
                                            .now(instant)
                                            .zone("Europe/Moscow")
                                            .build())
                            .passed())
                    .isTrue();
            assertThat(evaluate(
                                    item("ok"),
                                    samara,
                                    SuggestFilterContext.builder()
                                            .now(instant)
                                            .zone("Europe/Samara")
                                            .build())
                            .primaryCode())
                    .isEqualTo("outside_schedule");
        }
    }

    @Test
    @DisplayName("Aho-Corasick finds multiple patterns")
    void ahoCorasickMultiMatch() {
        AhoCorasick ac = AhoCorasick.build(List.of("abc", "bcd", "xyz"));
        assertThat(ac.findAll("xabcd")).containsExactlyInAnyOrder("abc", "bcd");
        assertThat(ac.findAll("nope")).isEmpty();
    }

    @Test
    @DisplayName("KeywordAutomatonCache reuses compiled automata")
    void automatonCacheReuse() {
        KeywordAutomatonCache cache = new KeywordAutomatonCache();
        Map<String, Object> config = filters(Map.of("keyword_include", List.of("news")));
        var first = cache.get(config);
        var second = cache.get(config);
        assertThat(first).isSameAs(second);
        assertThat(cache.size()).isEqualTo(1);

        SuggestFilterEngine engine = new SuggestFilterEngine(cache);
        assertThat(engine.evaluateWithCache(item("breaking news"), config, ctx())
                        .passed())
                .isTrue();
        assertThat(engine.cache()).isSameAs(cache);
    }

    @Test
    @DisplayName("defaults: signed=true, on_reject.delete, dedup 7d/0.8")
    void configDefaults() {
        Map<String, Object> config = SuggestConfigSupport.withDefaults(Map.of());
        assertThat(SuggestConfigSupport.signed(config)).isTrue();
        assertThat(SuggestConfigSupport.onRejectPolicy(config)).isEqualTo("delete");
        assertThat(SuggestConfigSupport.dedupLookbackDays(config)).isEqualTo(7);
        assertThat(SuggestConfigSupport.dedupThreshold(config)).isEqualTo(0.8d);
        assertThat(SuggestConfigSupport.configVersion(config)).isEqualTo(SuggestConfigSupport.CURRENT_CONFIG_VERSION);
    }

    @Test
    @DisplayName("author week and channel week limits")
    void weekLimits() {
        Map<String, Object> config = configWith(Map.of("limits", Map.of("author_per_week", 3, "channel_per_week", 10)));
        SuggestFilterContext ctx = SuggestFilterContext.builder()
                .now(Instant.parse("2026-11-18T12:00:00Z"))
                .authorPublishedLastWeek(3)
                .publishedLastWeek(10)
                .build();
        SuggestDecision decision = evaluate(item("ok"), config, ctx);
        assertThat(decision.reasonCodes()).contains("author_limit_week", "channel_limit_week");
    }

    @ParameterizedTest
    @ValueSource(strings = {"photo", "Photo", "IMAGE", "image"})
    @DisplayName("attachment type aliases for image/photo")
    void imageTypeAliases(String type) {
        Map<String, Object> config = filters(Map.of("skip_if_no_image", true));
        assertThat(code(withAtt("x", List.of(Map.of("type", type))), config)).isNull();
    }

    @ParameterizedTest
    @CsvSource({"зеленый, Зелёный лес, true", "зеленый, Зеленый лес, true", "зеленый, синий лес, false"})
    void yoNormalizationCsv(String needle, String text, boolean pass) {
        Map<String, Object> config = filters(Map.of("keyword_include", List.of(needle)));
        if (pass) {
            assertThat(code(item(text), config)).isNull();
        } else {
            assertThat(code(item(text), config)).isEqualTo("keyword_include");
        }
    }

    @Test
    @DisplayName("human-readable Russian messages present")
    void russianMessages() {
        Map<String, Object> config = filters(Map.of("stop_words", List.of("спам")));
        SuggestDecision decision = evaluate(item("это спам"), config, ctx());
        assertThat(decision.reasons().getFirst().message()).contains("стоп-слово");
    }

    @Test
    @DisplayName("empty item still evaluates")
    void nullSafeItem() {
        SuggestDecision decision = SuggestFilterEngine.evaluate(null, Map.of(), null);
        assertThat(decision.passed()).isTrue();
    }

    // --- helpers ---

    private static SuggestDecision evaluate(
            SuggestFilterItem item, Map<String, Object> config, SuggestFilterContext ctx) {
        return SuggestFilterEngine.evaluate(item, config, ctx);
    }

    private static String code(SuggestFilterItem item, Map<String, Object> config) {
        return evaluate(item, config, ctx()).primaryCode();
    }

    private static SuggestFilterContext ctx() {
        return SuggestFilterContext.empty(Instant.parse("2026-11-18T12:00:00Z"));
    }

    private static SuggestFilterItem item(String text) {
        return SuggestFilterItem.of(text, "1", List.of());
    }

    private static SuggestFilterItem item(String text, String authorVkId) {
        return SuggestFilterItem.of(text, authorVkId, List.of());
    }

    private static SuggestFilterItem withAtt(String text, List<Map<String, Object>> attachments) {
        return SuggestFilterItem.of(text, "1", attachments);
    }

    private static Map<String, Object> filters(Map<String, Object> filterOverrides) {
        Map<String, Object> filters = new HashMap<>(filterOverrides);
        return SuggestConfigSupport.withDefaults(Map.of("filters", filters));
    }

    private static Map<String, Object> configWith(Map<String, Object> overrides) {
        return SuggestConfigSupport.withDefaults(overrides);
    }

    private static Map<String, Object> scheduleConfig(String timezone, List<Map<String, Object>> windows) {
        return SuggestConfigSupport.withDefaults(Map.of(
                "schedule",
                Map.of(
                        "enabled", true,
                        "timezone", timezone,
                        "windows", windows)));
    }
}
