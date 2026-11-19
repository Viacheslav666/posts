package ru.posts.antispam.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.posts.antispam.model.AntispamActionKind;
import ru.posts.antispam.model.AntispamEventView;
import ru.posts.antispam.model.AntispamSection;
import ru.posts.antispam.model.CompiledRule;
import ru.posts.antispam.model.EvaluationContext;
import ru.posts.antispam.model.RuleConfig;
import ru.posts.antispam.model.Verdict;
import ru.posts.common.uuid.UuidV7;

class AntispamEngineTest {

    @Nested
    @DisplayName("TextNormalizer")
    class Normalizer {
        @ParameterizedTest
        @CsvSource({
            "спам,спам",
            "СПАМ,спам",
            "cпам,спам",
            "сп@м,спам",
            "с-п-а-м,спам",
            "с п а м,спам",
            "с.п.а.м,спам",
            "спаааам,спам",
            "Вк,вк"
        })
        void normalizesEvasion(String input, String expected) {
            assertThat(TextNormalizer.normalizeForKeywords(input)).isEqualTo(expected);
        }

        @Test
        void latinLookalikeSpamWord() {
            // latin c/p/a/m → cyrillic с/р/а/м → "срам" after map; use mixed "cпам"
            assertThat(TextNormalizer.normalizeForKeywords("cпам")).isEqualTo("спам");
            assertThat(TextNormalizer.normalizeForKeywords("sp@m")).contains("р");
        }

        @Test
        void emptyAndNull() {
            assertThat(TextNormalizer.normalizeForKeywords(null)).isEmpty();
            assertThat(TextNormalizer.normalizeForKeywords("")).isEmpty();
        }
    }

    @Nested
    @DisplayName("AhoCorasick")
    class Aho {
        @Test
        void findsNormalizedKeyword() {
            var matcher = AhoCorasickMatcher.compile(List.of("спам", "реклама"));
            assertThat(matcher.containsAny("купите cпам сейчас")).isTrue();
            assertThat(matcher.containsAny("с-п-а-м")).isTrue();
            assertThat(matcher.containsAny("норм текст")).isFalse();
        }

        @Test
        void emptyPatterns() {
            var matcher = AhoCorasickMatcher.compile(List.of("", "  "));
            assertThat(matcher.findAll("anything")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Engine filters")
    class Engine {
        @Test
        void keywordHitProducesDelete() {
            Verdict v = eval(
                    event("купи спам"),
                    rule(Map.of(
                            "filters", Map.of("keywords", Map.of("black", List.of("спам"))),
                            "actions", Map.of("delete", true))),
                    EvaluationContext.empty());
            assertThat(v.matched()).isTrue();
            assertThat(v.reasons()).anyMatch(r -> r.startsWith("keyword:"));
            assertThat(v.actions()).extracting(a -> a.kind()).contains(AntispamActionKind.DELETE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"сп@м", "cпам", "с-п-а-м", "С П А М", "спаааам"})
        void keywordNormalizationVariants(String text) {
            Verdict v = eval(
                    event(text),
                    rule(Map.of(
                            "filters", Map.of("keywords", Map.of("black", List.of("спам"))),
                            "actions", Map.of("delete", true))),
                    EvaluationContext.empty());
            assertThat(v.matched()).as(text).isTrue();
        }

        @Test
        void whiteKeywordAllows() {
            Verdict v = eval(
                    event("спам но белый"),
                    rule(Map.of(
                            "filters", Map.of("keywords", Map.of("black", List.of("спам"), "white", List.of("белый"))),
                            "actions", Map.of("delete", true))),
                    EvaluationContext.empty());
            assertThat(v.matched()).isFalse();
        }

        @Test
        void allLinks() {
            Verdict v = eval(
                    event("смотри https://evil.example/x"),
                    rule(Map.of(
                            "filters", Map.of("links", Map.of("all", true)),
                            "actions", Map.of("delete", true))),
                    EvaluationContext.empty());
            assertThat(v.reasons()).contains("link");
        }

        @Test
        void vkLinks() {
            Verdict v = eval(
                    event("https://vk.com/id1"),
                    rule(Map.of(
                            "filters", Map.of("links", Map.of("vk", true)),
                            "actions", Map.of("delete", true))),
                    EvaluationContext.empty());
            assertThat(v.reasons()).contains("vk_link");
        }

        @Test
        void domainWhitelistSkipsLink() {
            Verdict v = eval(
                    event("https://good.example/a"),
                    rule(Map.of(
                            "filters",
                                    Map.of("links", Map.of("all", true, "domain_whitelist", List.of("good.example"))),
                            "actions", Map.of("delete", true))),
                    EvaluationContext.empty());
            assertThat(v.matched()).isFalse();
        }

        @Test
        void photoAttachment() {
            Verdict v = eval(
                    AntispamEventView.of(
                            "vk", AntispamSection.WALL_COMMENT, "1", false, false, "", List.of("photo"), false, false),
                    rule(Map.of(
                            "filters", Map.of("attachments", Map.of("photo", true)),
                            "actions", Map.of("delete", true))),
                    EvaluationContext.empty());
            assertThat(v.reasons()).contains("attachment:photo");
        }

        @Test
        void photoWithoutText() {
            Verdict v = eval(
                    AntispamEventView.of(
                            "vk",
                            AntispamSection.WALL_COMMENT,
                            "1",
                            false,
                            false,
                            "  ",
                            List.of("photo"),
                            false,
                            false),
                    rule(Map.of(
                            "filters", Map.of("attachments", Map.of("photo_without_text", true)),
                            "actions", Map.of("delete", true))),
                    EvaluationContext.empty());
            assertThat(v.reasons()).contains("attachment:photo_without_text");
        }

        @Test
        void videoAudioDocGifSticker() {
            for (String type : List.of("video", "audio", "doc", "gif", "sticker")) {
                Verdict v = eval(
                        AntispamEventView.of(
                                "vk",
                                AntispamSection.WALL_COMMENT,
                                "1",
                                false,
                                false,
                                "x",
                                List.of(type),
                                false,
                                false),
                        rule(Map.of(
                                "filters", Map.of("attachments", Map.of(type, true)),
                                "actions", Map.of("delete", true))),
                        EvaluationContext.empty());
                assertThat(v.matched()).as(type).isTrue();
            }
        }

        @Test
        void caps() {
            Verdict v = eval(
                    event("ЭТО ВЕСЬ КАПС ТЕКСТ!!!"),
                    rule(Map.of(
                            "filters", Map.of("caps", Map.of("enabled", true, "ratio", 0.6, "min_letters", 5)),
                            "actions", Map.of("delete", true))),
                    EvaluationContext.empty());
            assertThat(v.reasons()).contains("caps");
        }

        @Test
        void fromCommunity() {
            AntispamEventView e = AntispamEventView.of(
                    "vk", AntispamSection.WALL_COMMENT, "-10", true, false, "hi", List.of(), false, false);
            Verdict v = eval(
                    e,
                    rule(Map.of(
                            "filters", Map.of("authors", Map.of("communities", true)),
                            "actions", Map.of("delete", true))),
                    EvaluationContext.empty());
            assertThat(v.reasons()).contains("from_community");
        }

        @Test
        void nonMember() {
            Verdict v = eval(
                    event("hi"),
                    rule(Map.of(
                            "filters", Map.of("authors", Map.of("non_members", true)),
                            "actions", Map.of("delete", true))),
                    new EvaluationContext(false, false, false, false, false, false, false, false, "-1", Set.of()));
            assertThat(v.reasons()).contains("non_member");
        }

        @Test
        void underAdsOnlyWhenEnabled() {
            EvaluationContext ctx =
                    new EvaluationContext(false, true, true, false, false, false, false, false, "-1", Set.of());
            Verdict off = eval(event("hi"), rule(Map.of("actions", Map.of("delete", true))), ctx);
            assertThat(off.matched()).isFalse();
            Verdict on = eval(
                    event("hi"),
                    rule(Map.of(
                            "filters", Map.of("context", Map.of("under_ads", true)),
                            "actions", Map.of("delete", true))),
                    ctx);
            assertThat(on.reasons()).contains("under_ads");
        }

        @Test
        void underPinned() {
            EvaluationContext ctx =
                    new EvaluationContext(false, true, false, true, false, false, false, false, "-1", Set.of());
            Verdict v = eval(
                    event("hi"),
                    rule(Map.of(
                            "filters", Map.of("context", Map.of("under_pinned", true)),
                            "actions", Map.of("delete", true))),
                    ctx);
            assertThat(v.reasons()).contains("under_pinned");
        }

        @Test
        void repeat() {
            EvaluationContext ctx =
                    new EvaluationContext(false, true, false, false, false, false, false, true, "-1", Set.of());
            Verdict v = eval(
                    event("same"),
                    rule(Map.of(
                            "filters", Map.of("repeat", Map.of("enabled", true)),
                            "actions", Map.of("delete", true))),
                    ctx);
            assertThat(v.reasons()).contains("repeat");
        }

        @Test
        void senderChat() {
            AntispamEventView e = AntispamEventView.of(
                    "tg", AntispamSection.TG_DISCUSSION, "1", false, false, "hi", List.of(), false, true);
            Verdict v = eval(
                    e,
                    rule(Map.of(
                            "filters", Map.of("authors", Map.of("sender_chat", true)),
                            "actions", Map.of("delete", true))),
                    EvaluationContext.empty());
            assertThat(v.reasons()).contains("sender_chat");
        }

        @Test
        void dryRunOnlyFlags() {
            CompiledRule r = rule(
                    Map.of(
                            "filters", Map.of("keywords", Map.of("black", List.of("спам"))),
                            "actions",
                                    Map.of(
                                            "delete",
                                            true,
                                            "ban",
                                            Map.of("enabled", true),
                                            "reply",
                                            Map.of("enabled", true))),
                    true);
            Verdict v = eval(event("спам"), r, EvaluationContext.empty());
            assertThat(v.dryRun()).isTrue();
            assertThat(v.actions()).hasSize(1);
            assertThat(v.actions().getFirst().kind()).isEqualTo(AntispamActionKind.FLAG);
        }

        @Test
        void replyThenDeleteThenBanOrder() {
            Verdict v = eval(
                    event("спам"),
                    rule(Map.of(
                            "filters", Map.of("keywords", Map.of("black", List.of("спам"))),
                            "actions",
                                    Map.of(
                                            "delete",
                                            true,
                                            "reply",
                                            Map.of("enabled", true, "template", "bye {reasons}"),
                                            "ban",
                                            Map.of("enabled", true, "days", 7)))),
                    EvaluationContext.empty());
            assertThat(v.actions())
                    .extracting(a -> a.kind())
                    .containsExactly(AntispamActionKind.REPLY, AntispamActionKind.DELETE, AntispamActionKind.BAN);
        }

        @Test
        void adminNeverTouched() {
            EvaluationContext ctx =
                    new EvaluationContext(true, true, false, false, false, false, false, false, "-1", Set.of());
            Verdict v = eval(
                    event("спам"),
                    rule(Map.of(
                            "filters", Map.of("keywords", Map.of("black", List.of("спам"))),
                            "actions", Map.of("delete", true))),
                    ctx);
            assertThat(v.matched()).isFalse();
        }

        @Test
        void userWhitelist() {
            Verdict v = eval(
                    event("спам"),
                    rule(Map.of(
                            "filters", Map.of("keywords", Map.of("black", List.of("спам"))),
                            "actions", Map.of("delete", true),
                            "exceptions", Map.of("user_whitelist", List.of("42")))),
                    EvaluationContext.empty());
            // author in event() is "42"
            assertThat(v.matched()).isFalse();
        }

        @Test
        void ownCommunityExcluded() {
            EvaluationContext ctx =
                    new EvaluationContext(false, true, false, false, false, false, true, false, "-100", Set.of());
            Verdict v = eval(
                    event("спам"),
                    rule(Map.of(
                            "filters", Map.of("keywords", Map.of("black", List.of("спам"))),
                            "actions", Map.of("delete", true))),
                    ctx);
            assertThat(v.matched()).isFalse();
        }

        @Test
        void automaticForwardSkipped() {
            AntispamEventView e = AntispamEventView.of(
                    "tg", AntispamSection.TG_DISCUSSION, "1", false, false, "спам", List.of(), true, false);
            Verdict v = eval(
                    e,
                    rule(Map.of(
                            "filters", Map.of("keywords", Map.of("black", List.of("спам"))),
                            "actions", Map.of("delete", true))),
                    EvaluationContext.empty());
            assertThat(v.matched()).isFalse();
        }

        @Test
        void firstPriorityWins() {
            CompiledRule high = ruleWithPriority(
                    10,
                    Map.of(
                            "filters", Map.of("keywords", Map.of("black", List.of("спам"))),
                            "actions", Map.of("delete", true)));
            CompiledRule low = ruleWithPriority(
                    20,
                    Map.of(
                            "filters", Map.of("links", Map.of("all", true)),
                            "actions", Map.of("delete", true)));
            Verdict v =
                    AntispamEngine.evaluate(event("спам https://x.com"), List.of(high, low), EvaluationContext.empty());
            assertThat(v.ruleId()).isEqualTo(high.ruleId());
            assertThat(v.reasons()).anyMatch(r -> r.startsWith("keyword:"));
        }

        @Test
        void sectionMismatchSkipped() {
            CompiledRule r = new CompiledRule(
                    UuidV7.generate(),
                    "r",
                    1,
                    1,
                    false,
                    Set.of("photo_comment"),
                    RuleConfig.fromMap(Map.of(
                            "filters", Map.of("keywords", Map.of("black", List.of("спам"))),
                            "actions", Map.of("delete", true))),
                    AhoCorasickMatcher.compile(List.of("спам")),
                    AhoCorasickMatcher.compile(List.of()));
            Verdict v = eval(event("спам"), r, EvaluationContext.empty());
            assertThat(v.matched()).isFalse();
        }

        @Test
        void emptyRules() {
            assertThat(AntispamEngine.evaluate(event("x"), List.of(), EvaluationContext.empty())
                            .matched())
                    .isFalse();
        }
    }

    private static AntispamEventView event(String text) {
        return AntispamEventView.of(
                "vk", AntispamSection.WALL_COMMENT, "42", false, false, text, List.of(), false, false);
    }

    private static CompiledRule rule(Map<String, Object> config) {
        return rule(config, false);
    }

    private static CompiledRule rule(Map<String, Object> config, boolean dryRun) {
        return ruleWithPriority(100, config, dryRun);
    }

    private static CompiledRule ruleWithPriority(int priority, Map<String, Object> config) {
        return ruleWithPriority(priority, config, false);
    }

    private static CompiledRule ruleWithPriority(int priority, Map<String, Object> config, boolean dryRun) {
        RuleConfig cfg = RuleConfig.fromMap(config);
        return new CompiledRule(
                UuidV7.generate(),
                "rule",
                priority,
                1,
                dryRun,
                Set.of(),
                cfg,
                AhoCorasickMatcher.compile(cfg.blackWords()),
                AhoCorasickMatcher.compile(cfg.whiteWords()));
    }

    private static Verdict eval(AntispamEventView event, CompiledRule rule, EvaluationContext ctx) {
        return AntispamEngine.evaluate(event, List.of(rule), ctx);
    }
}
