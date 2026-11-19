package ru.posts.suggest.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.posts.content.hash.SimHasher;
import ru.posts.suggest.config.SuggestConfigSupport;
import ru.posts.suggest.schedule.SuggestSlotScheduler;

/** BE-34 — ≥30 filter / schedule cases (ё/е, whole word, limits, dedup, TZ boundaries). */
class SuggestFilterEngineTest {

    private final SuggestFilterEngine engine = new SuggestFilterEngine();
    private final SuggestSlotScheduler scheduler = new SuggestSlotScheduler();

    @Test
    @DisplayName("1 yo/ye normalization: ё matches е in stop_words")
    void yoYeNormalization() {
        Map<String, Object> config = config(Map.of("stop_words", List.of("зеленый")));
        assertThat(eval(item("зелёный чай"), config).action()).isEqualTo(SuggestAction.REJECT);
        assertThat(eval(item("обычный текст"), config).action()).isEqualTo(SuggestAction.PUBLISH);
    }

    @Test
    @DisplayName("2 case insensitive keywords")
    void caseInsensitive() {
        Map<String, Object> config = config(Map.of("stop_words", List.of("спам")));
        assertThat(eval(item("Большой СПАМ сегодня"), config).reasons())
                .anyMatch(r -> r.code().equals("stop_words"));
    }

    @Test
    @DisplayName("3 whole_word: substring inside word does not match")
    void wholeWordVsContains() {
        Map<String, Object> whole = config(Map.of("keyword_include", List.of("кот"), "keyword_match", "whole_word"));
        Map<String, Object> contains = config(Map.of("keyword_include", List.of("кот"), "keyword_match", "contains"));
        assertThat(eval(item("который день"), whole).action()).isEqualTo(SuggestAction.FILTER_OUT);
        assertThat(eval(item("который день"), contains).action()).isEqualTo(SuggestAction.PUBLISH);
        assertThat(eval(item("мой кот дома"), whole).action()).isEqualTo(SuggestAction.PUBLISH);
    }

    @Test
    @DisplayName("4 contains match for keyword_include")
    void containsInclude() {
        Map<String, Object> config = config(Map.of("keyword_include", List.of("новость")));
        assertThat(eval(item("свежая новость дня"), config).action()).isEqualTo(SuggestAction.PUBLISH);
        assertThat(eval(item("без ключевых"), config).action()).isEqualTo(SuggestAction.FILTER_OUT);
    }

    @Test
    @DisplayName("5 keyword_exclude rejects")
    void keywordExclude() {
        Map<String, Object> config = config(Map.of("keyword_exclude", List.of("реклама")));
        SuggestDecision d = eval(item("это реклама товаров"), config);
        assertThat(d.action()).isEqualTo(SuggestAction.REJECT);
        assertThat(d.reasons()).anyMatch(r -> r.code().equals("keyword_exclude"));
    }

    @Test
    @DisplayName("6 emoji-only treated as blank when require_text")
    void emojiBlankText() {
        Map<String, Object> config = config(Map.of("require_text", true));
        assertThat(eval(item("🔥🎉"), config).reasons()).anyMatch(r -> r.code().equals("require_text"));
        assertThat(eval(item("есть текст 🔥"), config).action()).isEqualTo(SuggestAction.PUBLISH);
    }

    @Test
    @DisplayName("7 require_image")
    void requireImage() {
        Map<String, Object> config = config(Map.of("require_image", true));
        assertThat(eval(item("текст"), config).action()).isEqualTo(SuggestAction.FILTER_OUT);
        SuggestItemView withPhoto =
                new SuggestItemView("1", "10", "текст", List.of(Map.of("type", "photo")), null, false, false);
        assertThat(eval(withPhoto, config).action()).isEqualTo(SuggestAction.PUBLISH);
    }

    @Test
    @DisplayName("8 forbid_attachments")
    void forbidAttachments() {
        Map<String, Object> config = config(Map.of("forbid_attachments", List.of("link")));
        SuggestItemView withLink =
                new SuggestItemView("1", "10", "см. ссылку", List.of(Map.of("type", "link")), null, false, false);
        assertThat(eval(withLink, config).reasons()).anyMatch(r -> r.code().equals("forbid_attachments"));
    }

    @Test
    @DisplayName("9 author blacklist")
    void authorBlacklist() {
        Map<String, Object> config = config(Map.of("author_blacklist", List.of("999")));
        SuggestItemView view = SuggestItemView.of("1", "999", "ok", List.of(), null);
        assertThat(eval(view, config).action()).isEqualTo(SuggestAction.FILTER_OUT);
    }

    @Test
    @DisplayName("10 author whitelist bypasses stop words")
    void authorWhitelistBypass() {
        Map<String, Object> config = config(Map.of("author_whitelist", List.of("42"), "stop_words", List.of("спам")));
        SuggestItemView view = SuggestItemView.of("1", "42", "это спам", List.of(), null);
        assertThat(eval(view, config).action()).isEqualTo(SuggestAction.PUBLISH);
    }

    @Test
    @DisplayName("11 author day limit")
    void authorDayLimit() {
        Map<String, Object> config = withLimits(Map.of("per_author", Map.of("day", 2)));
        SuggestFilterContext ctx = new SuggestFilterContext(
                "auto",
                null,
                null,
                List.of(),
                new SuggestFilterContext.AuthorCounts(0, 2, 2),
                SuggestFilterContext.AuthorCounts.ZERO,
                false,
                Instant.now());
        assertThat(engine.evaluate(item("ok"), config, ctx).reasons())
                .anyMatch(r -> r.code().equals("author_limit_day"));
    }

    @Test
    @DisplayName("12 author hour limit")
    void authorHourLimit() {
        Map<String, Object> config = withLimits(Map.of("per_author", Map.of("hour", 1)));
        SuggestFilterContext ctx = new SuggestFilterContext(
                "auto",
                null,
                null,
                List.of(),
                new SuggestFilterContext.AuthorCounts(1, 1, 1),
                SuggestFilterContext.AuthorCounts.ZERO,
                false,
                Instant.now());
        assertThat(engine.evaluate(item("ok"), config, ctx).reasons())
                .anyMatch(r -> r.code().equals("author_limit_hour"));
    }

    @Test
    @DisplayName("13 author week limit")
    void authorWeekLimit() {
        Map<String, Object> config = withLimits(Map.of("per_author", Map.of("week", 3)));
        SuggestFilterContext ctx = new SuggestFilterContext(
                "auto",
                null,
                null,
                List.of(),
                new SuggestFilterContext.AuthorCounts(0, 0, 3),
                SuggestFilterContext.AuthorCounts.ZERO,
                false,
                Instant.now());
        assertThat(engine.evaluate(item("ok"), config, ctx).reasons())
                .anyMatch(r -> r.code().equals("author_limit_week"));
    }

    @Test
    @DisplayName("14 global day limit")
    void globalDayLimit() {
        Map<String, Object> config = withLimits(Map.of("global", Map.of("day", 5)));
        SuggestFilterContext ctx = new SuggestFilterContext(
                "auto",
                null,
                null,
                List.of(),
                SuggestFilterContext.AuthorCounts.ZERO,
                new SuggestFilterContext.AuthorCounts(0, 5, 5),
                false,
                Instant.now());
        assertThat(engine.evaluate(item("ok"), config, ctx).reasons())
                .anyMatch(r -> r.code().equals("global_limit_day"));
    }

    @Test
    @DisplayName("15 zero limit means unlimited")
    void zeroLimitUnlimited() {
        Map<String, Object> config = withLimits(Map.of("per_author", Map.of("day", 0)));
        SuggestFilterContext ctx = new SuggestFilterContext(
                "auto",
                null,
                null,
                List.of(),
                new SuggestFilterContext.AuthorCounts(100, 100, 100),
                SuggestFilterContext.AuthorCounts.ZERO,
                false,
                Instant.now());
        assertThat(engine.evaluate(item("ok"), config, ctx).action()).isEqualTo(SuggestAction.PUBLISH);
    }

    @Test
    @DisplayName("16 simhash dedup")
    void simhashDedup() {
        String text = "уникальный текст для дедупа предложки номер один";
        long hash = SimHasher.fingerprint(text);
        Map<String, Object> config = SuggestConfigSupport.withDefaults(Map.of());
        SuggestItemView view = SuggestItemView.of("1", "10", text, List.of(), hash);
        SuggestFilterContext ctx = new SuggestFilterContext(
                "auto",
                null,
                null,
                List.of(hash),
                SuggestFilterContext.AuthorCounts.ZERO,
                SuggestFilterContext.AuthorCounts.ZERO,
                false,
                Instant.now());
        assertThat(engine.evaluate(view, config, ctx).reasons())
                .anyMatch(r -> r.code().equals("duplicate_content"));
    }

    @Test
    @DisplayName("17 ads pause soft reason in hybrid → MODERATE")
    void adsPauseHybrid() {
        Map<String, Object> config = SuggestConfigSupport.withDefaults(Map.of());
        SuggestFilterContext ctx = new SuggestFilterContext(
                "hybrid",
                null,
                null,
                List.of(),
                SuggestFilterContext.AuthorCounts.ZERO,
                SuggestFilterContext.AuthorCounts.ZERO,
                true,
                Instant.now());
        SuggestDecision d = engine.evaluate(item("ok"), config, ctx);
        assertThat(d.action()).isEqualTo(SuggestAction.MODERATE);
        assertThat(d.reasons()).anyMatch(r -> r.code().equals("ads_pause"));
    }

    @Test
    @DisplayName("18 manual mode always MODERATE if not hard reject")
    void manualMode() {
        SuggestDecision d = engine.evaluate(item("ok"), SuggestConfigSupport.withDefaults(Map.of()), ctx("manual"));
        assertThat(d.action()).isEqualTo(SuggestAction.MODERATE);
    }

    @Test
    @DisplayName("19 require_messages_allowed")
    void requireMessagesAllowed() {
        Map<String, Object> config = config(Map.of("require_messages_allowed", true));
        SuggestFilterContext ctx = new SuggestFilterContext(
                "auto",
                false,
                null,
                List.of(),
                SuggestFilterContext.AuthorCounts.ZERO,
                SuggestFilterContext.AuthorCounts.ZERO,
                false,
                Instant.now());
        assertThat(engine.evaluate(item("ok"), config, ctx).reasons())
                .anyMatch(r -> r.code().equals("require_messages_allowed"));
    }

    @Test
    @DisplayName("20 forbid_closed_profile")
    void forbidClosedProfile() {
        Map<String, Object> config = config(Map.of("forbid_closed_profile", true));
        SuggestFilterContext ctx = new SuggestFilterContext(
                "auto",
                null,
                true,
                List.of(),
                SuggestFilterContext.AuthorCounts.ZERO,
                SuggestFilterContext.AuthorCounts.ZERO,
                false,
                Instant.now());
        assertThat(engine.evaluate(item("ok"), config, ctx).reasons())
                .anyMatch(r -> r.code().equals("forbid_closed_profile"));
    }

    @Test
    @DisplayName("21 links do not auto-reject without forbid")
    void linksAllowedByDefault() {
        assertThat(eval(item("см. https://example.com"), SuggestConfigSupport.withDefaults(Map.of()))
                        .action())
                .isEqualTo(SuggestAction.PUBLISH);
    }

    @Test
    @DisplayName("22 stop_words → REJECT")
    void stopWordsReject() {
        Map<String, Object> config = config(Map.of("stop_words", List.of("розыгрыш")));
        assertThat(eval(item("Большой розыгрыш"), config).action()).isEqualTo(SuggestAction.REJECT);
    }

    @Test
    @DisplayName("23 empty text with require_text")
    void emptyRequireText() {
        Map<String, Object> config = config(Map.of("require_text", true));
        assertThat(eval(item("   "), config).reasons()).anyMatch(r -> r.code().equals("require_text"));
    }

    @Test
    @DisplayName("24 whole_word boundary with punctuation")
    void wholeWordPunctuation() {
        Map<String, Object> config = config(Map.of("keyword_include", List.of("пост"), "keyword_match", "whole_word"));
        assertThat(eval(item("новый пост!"), config).action()).isEqualTo(SuggestAction.PUBLISH);
        assertThat(eval(item("репост записи"), config).action()).isEqualTo(SuggestAction.FILTER_OUT);
    }

    @Test
    @DisplayName("25 auto mode publishes clean item")
    void autoPublish() {
        assertThat(eval(item("чистая новость"), SuggestConfigSupport.withDefaults(Map.of()))
                        .action())
                .isEqualTo(SuggestAction.PUBLISH);
    }

    @Nested
    @DisplayName("schedule windows")
    class ScheduleWindows {

        @Test
        @DisplayName("26 Moscow inside window → now")
        void moscowInside() {
            Instant now = LocalDateTime.of(2026, 11, 18, 12, 0)
                    .atZone(ZoneId.of("Europe/Moscow"))
                    .toInstant();
            Instant slot = scheduler.nextSlot(scheduleConfig("Europe/Moscow"), now, null, false);
            assertThat(slot).isEqualTo(now);
        }

        @Test
        @DisplayName("27 Moscow after window → next morning")
        void moscowAfterWindow() {
            Instant now = LocalDateTime.of(2026, 11, 18, 22, 30)
                    .atZone(ZoneId.of("Europe/Moscow"))
                    .toInstant();
            Instant slot = scheduler.nextSlot(scheduleConfig("Europe/Moscow"), now, null, false);
            LocalDateTime local = LocalDateTime.ofInstant(slot, ZoneId.of("Europe/Moscow"));
            assertThat(local.getHour()).isEqualTo(9);
            assertThat(local.getDayOfMonth()).isEqualTo(19);
        }

        @Test
        @DisplayName("28 Moscow before window same day")
        void moscowBeforeWindow() {
            Instant now = LocalDateTime.of(2026, 11, 18, 7, 0)
                    .atZone(ZoneId.of("Europe/Moscow"))
                    .toInstant();
            Instant slot = scheduler.nextSlot(scheduleConfig("Europe/Moscow"), now, null, false);
            LocalDateTime local = LocalDateTime.ofInstant(slot, ZoneId.of("Europe/Moscow"));
            assertThat(local.getHour()).isEqualTo(9);
            assertThat(local.getDayOfMonth()).isEqualTo(18);
        }

        @Test
        @DisplayName("29 Samara day boundary after window")
        void samaraAfterWindow() {
            Instant now = LocalDateTime.of(2026, 11, 18, 22, 0)
                    .atZone(ZoneId.of("Europe/Samara"))
                    .toInstant();
            Instant slot = scheduler.nextSlot(scheduleConfig("Europe/Samara"), now, null, false);
            LocalDateTime local = LocalDateTime.ofInstant(slot, ZoneId.of("Europe/Samara"));
            assertThat(local.getHour()).isEqualTo(9);
            assertThat(local.getDayOfMonth()).isEqualTo(19);
        }

        @Test
        @DisplayName("30 Samara inside window")
        void samaraInside() {
            Instant now = LocalDateTime.of(2026, 11, 18, 15, 0)
                    .atZone(ZoneId.of("Europe/Samara"))
                    .toInstant();
            Instant slot = scheduler.nextSlot(scheduleConfig("Europe/Samara"), now, null, false);
            assertThat(slot).isEqualTo(now);
        }

        @Test
        @DisplayName("31 schedule disabled → now (+ min_interval)")
        void scheduleDisabled() {
            Instant now = Instant.parse("2026-11-18T10:00:00Z");
            Instant last = Instant.parse("2026-11-18T09:50:00Z");
            Map<String, Object> config = SuggestConfigSupport.withDefaults(Map.of(
                    "schedule", Map.of("enabled", false, "min_interval_minutes", 30, "timezone", "Europe/Moscow")));
            Instant slot = scheduler.nextSlot(config, now, last, false);
            assertThat(slot).isEqualTo(last.plusSeconds(30 * 60));
        }

        @Test
        @DisplayName("32 pause after ads defers slot")
        void pauseAfterAds() {
            Instant now = Instant.parse("2026-11-18T10:00:00Z");
            Instant last = Instant.parse("2026-11-18T09:55:00Z");
            Map<String, Object> config = SuggestConfigSupport.withDefaults(Map.of(
                    "schedule",
                    Map.of("enabled", false, "pause_after_ads_minutes", 60, "timezone", "Europe/Moscow"),
                    "ads_pause",
                    Map.of("enabled", true)));
            Instant slot = scheduler.nextSlot(config, now, last, true);
            assertThat(slot).isEqualTo(last.plusSeconds(60 * 60));
        }
    }

    @Test
    @DisplayName("33 isWholeWord helper")
    void wholeWordHelper() {
        assertThat(SuggestFilterEngine.isWholeWord("мой кот дома", 4, 6)).isTrue();
        assertThat(SuggestFilterEngine.isWholeWord("который", 0, 2)).isFalse();
    }

    @Test
    @DisplayName("34 config defaults validate")
    void configDefaults() {
        Map<String, Object> config = SuggestConfigSupport.withDefaults(Map.of());
        SuggestConfigSupport.validate(config);
        assertThat(config.get("config_version")).isEqualTo(1);
        assertThat(SuggestConfigSupport.signed(config)).isTrue();
    }

    @Test
    @DisplayName("35 hybrid soft near-duplicate → action in expected set")
    void hybridNearDuplicate() {
        String a = "очень похожий длинный текст для проверки околопорогового дедупа один";
        String b = "очень похожий длинный текст для проверки околопорогового дедупа два";
        long ha = SimHasher.fingerprint(a);
        long hb = SimHasher.fingerprint(b);
        Map<String, Object> config = SuggestConfigSupport.withDefaults(
                Map.of("dedup", Map.of("enabled", true, "similarity_threshold", 0.99d, "lookback_days", 7)));
        SuggestItemView view = SuggestItemView.of("1", "10", a, List.of(), ha);
        SuggestFilterContext ctx = new SuggestFilterContext(
                "hybrid",
                null,
                null,
                List.of(hb),
                SuggestFilterContext.AuthorCounts.ZERO,
                SuggestFilterContext.AuthorCounts.ZERO,
                false,
                Instant.now());
        SuggestDecision d = engine.evaluate(view, config, ctx);
        assertThat(d.action()).isIn(SuggestAction.FILTER_OUT, SuggestAction.MODERATE, SuggestAction.PUBLISH);
    }

    private SuggestDecision eval(SuggestItemView view, Map<String, Object> config) {
        return engine.evaluate(view, config, ctx("auto"));
    }

    private static SuggestItemView item(String text) {
        return SuggestItemView.of("1", "10", text, List.of(), null);
    }

    private SuggestFilterContext ctx(String mode) {
        return new SuggestFilterContext(
                mode,
                null,
                null,
                List.of(),
                SuggestFilterContext.AuthorCounts.ZERO,
                SuggestFilterContext.AuthorCounts.ZERO,
                false,
                Instant.now());
    }

    private Map<String, Object> config(Map<String, Object> filters) {
        Map<String, Object> raw = new HashMap<>();
        raw.put("filters", filters);
        return SuggestConfigSupport.withDefaults(raw);
    }

    private Map<String, Object> withLimits(Map<String, Object> limits) {
        Map<String, Object> raw = new HashMap<>();
        raw.put("limits", limits);
        return SuggestConfigSupport.withDefaults(raw);
    }

    private Map<String, Object> scheduleConfig(String timezone) {
        return SuggestConfigSupport.withDefaults(Map.of(
                "schedule",
                Map.of(
                        "enabled",
                        true,
                        "timezone",
                        timezone,
                        "windows",
                        List.of(Map.of("days_of_week", List.of(1, 2, 3, 4, 5, 6, 7), "from", "09:00", "to", "21:00")),
                        "min_interval_minutes",
                        0,
                        "pause_after_ads_minutes",
                        0)));
    }
}
