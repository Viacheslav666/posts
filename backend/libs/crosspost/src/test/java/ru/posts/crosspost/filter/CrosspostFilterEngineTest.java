package ru.posts.crosspost.filter;

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
import ru.posts.crosspost.config.CrosspostConfigSupport;
import ru.posts.crosspost.model.CrosspostCandidate;

class CrosspostFilterEngineTest {

    @Test
    void nullCandidate() {
        assertThat(CrosspostFilterEngine.firstReason(null, Map.of())).isEqualTo("empty_candidate");
    }

    @Test
    @DisplayName("ad posts excluded by default (erid / Реклама / marked_as_ads)")
    void adPostExcluded() {
        Map<String, Object> config = defaults();
        assertThat(CrosspostFilterEngine.firstReason(base().hasErid(true).build(), config))
                .isEqualTo("ad_post_excluded");
        assertThat(CrosspostFilterEngine.firstReason(base().markedAsAds(true).build(), config))
                .isEqualTo("ad_post_excluded");
        assertThat(CrosspostFilterEngine.firstReason(base().text("Текст erid=AbC123").build(), config))
                .isEqualTo("ad_post_excluded");
        assertThat(CrosspostFilterEngine.firstReason(base().text("Это Реклама продукта").build(), config))
                .isEqualTo("ad_post_excluded");
        assertThat(CrosspostFilterEngine.firstReason(base().text("обычный пост").build(), config))
                .isNull();
    }

    @Test
    void adCanBeAllowed() {
        Map<String, Object> config = filters(Map.of("exclude_ads", false));
        assertThat(CrosspostFilterEngine.firstReason(base().markedAsAds(true).build(), config))
                .isNull();
    }

    @Nested
    class LinksSnippetsArticles {
        @Test
        void excludeLinksInText() {
            Map<String, Object> config = filters(Map.of("exclude_links", true, "exclude_ads", false));
            assertThat(CrosspostFilterEngine.firstReason(base().text("см https://example.com").build(), config))
                    .isEqualTo("exclude_links");
            assertThat(CrosspostFilterEngine.firstReason(base().text("без ссылок").build(), config))
                    .isNull();
        }

        @Test
        void excludeLinkAttachment() {
            Map<String, Object> config = filters(Map.of("exclude_links", true, "exclude_ads", false));
            assertThat(CrosspostFilterEngine.firstReason(base().hasLinkAttachment(true).build(), config))
                    .isEqualTo("exclude_links");
        }

        @Test
        void excludeSnippets() {
            Map<String, Object> config = filters(Map.of("exclude_ads", false));
            assertThat(CrosspostFilterEngine.firstReason(base().hasSnippet(true).build(), config))
                    .isEqualTo("exclude_snippets");
        }

        @Test
        void excludeArticles() {
            Map<String, Object> config = filters(Map.of("exclude_ads", false));
            assertThat(CrosspostFilterEngine.firstReason(base().hasArticle(true).build(), config))
                    .isEqualTo("exclude_articles");
        }

        @Test
        void allowSnippetsWhenDisabled() {
            Map<String, Object> config = filters(Map.of("exclude_ads", false, "exclude_snippets", false));
            assertThat(CrosspostFilterEngine.firstReason(base().hasSnippet(true).build(), config))
                    .isNull();
        }
    }

    @Nested
    class MediaAndText {
        @Test
        void skipIfNoText() {
            Map<String, Object> config = filters(Map.of("exclude_ads", false, "skip_if_no_text", true));
            assertThat(CrosspostFilterEngine.firstReason(base().text("").build(), config))
                    .isEqualTo("skip_if_no_text");
            assertThat(CrosspostFilterEngine.firstReason(base().text("hi").build(), config))
                    .isNull();
        }

        @Test
        void skipIfNoImages() {
            Map<String, Object> config = filters(Map.of("exclude_ads", false, "skip_if_no_images", true));
            assertThat(CrosspostFilterEngine.firstReason(base().hasImage(false).build(), config))
                    .isEqualTo("skip_if_no_images");
            assertThat(CrosspostFilterEngine.firstReason(base().hasImage(true).build(), config))
                    .isNull();
        }

        @Test
        void excludeVideo() {
            Map<String, Object> config = filters(Map.of("exclude_ads", false, "exclude_video", true));
            assertThat(CrosspostFilterEngine.firstReason(base().hasVideo(true).build(), config))
                    .isEqualTo("exclude_video");
        }

        @Test
        void excludeClipsDefault() {
            Map<String, Object> config = filters(Map.of("exclude_ads", false));
            assertThat(CrosspostFilterEngine.firstReason(base().hasClip(true).build(), config))
                    .isEqualTo("exclude_clips");
        }

        @Test
        void excludePollGeoAudioDefault() {
            Map<String, Object> config = filters(Map.of("exclude_ads", false));
            assertThat(CrosspostFilterEngine.firstReason(base().hasPoll(true).build(), config))
                    .isEqualTo("exclude_poll");
            assertThat(CrosspostFilterEngine.firstReason(base().hasGeo(true).build(), config))
                    .isEqualTo("exclude_geo");
            assertThat(CrosspostFilterEngine.firstReason(base().hasAudio(true).build(), config))
                    .isEqualTo("exclude_audio");
        }

        @Test
        void allowPollWhenDisabled() {
            Map<String, Object> config = filters(Map.of("exclude_ads", false, "exclude_poll", false));
            assertThat(CrosspostFilterEngine.firstReason(base().hasPoll(true).build(), config))
                    .isNull();
        }
    }

    @Nested
    class Reposts {
        @Test
        void excludeRepostsWhenConfigured() {
            Map<String, Object> config = filters(Map.of("exclude_ads", false, "exclude_reposts", true));
            assertThat(CrosspostFilterEngine.firstReason(base().isRepost(true).build(), config))
                    .isEqualTo("exclude_reposts");
        }

        @Test
        void includeRepostsFalse() {
            Map<String, Object> config = filters(Map.of("exclude_ads", false, "include_reposts", false));
            assertThat(CrosspostFilterEngine.firstReason(base().isRepost(true).build(), config))
                    .isEqualTo("exclude_reposts");
        }

        @Test
        void allowRepostsByDefault() {
            Map<String, Object> config = filters(Map.of("exclude_ads", false));
            assertThat(CrosspostFilterEngine.firstReason(base().isRepost(true).build(), config))
                    .isNull();
        }
    }

    @Nested
    class Keywords {
        @Test
        void keywordInclude() {
            Map<String, Object> config = filters(Map.of(
                    "exclude_ads", false,
                    "keyword_include", List.of("новость", "апдейт")));
            assertThat(CrosspostFilterEngine.firstReason(base().text("важная Новость дня").build(), config))
                    .isNull();
            assertThat(CrosspostFilterEngine.firstReason(base().text("просто текст").build(), config))
                    .isEqualTo("keyword_include");
        }

        @Test
        void keywordExclude() {
            Map<String, Object> config = filters(Map.of(
                    "exclude_ads", false,
                    "keyword_exclude", List.of("спам")));
            assertThat(CrosspostFilterEngine.firstReason(base().text("это СПАМ").build(), config))
                    .isEqualTo("keyword_exclude");
            assertThat(CrosspostFilterEngine.firstReason(base().text("ок").build(), config))
                    .isNull();
        }
    }

    @Nested
    class WorkingWindow {
        @Test
        void outsideWorkingWindow() {
            Map<String, Object> config = CrosspostConfigSupport.withDefaults(Map.of(
                    "schedule",
                    Map.of(
                            "enabled",
                            true,
                            "timezone",
                            "UTC",
                            "days_of_week",
                            List.of(1, 2, 3, 4, 5, 6, 7),
                            "hours_from",
                            9,
                            "hours_to",
                            10)));
            Instant night = Instant.parse("2026-11-19T02:00:00Z");
            assertThat(CrosspostFilterEngine.evaluate(base().build(), config, night))
                    .contains("outside_working_window");
        }

        @Test
        void insideWorkingWindow() {
            Map<String, Object> config = CrosspostConfigSupport.withDefaults(Map.of(
                    "filters", Map.of("exclude_ads", false),
                    "schedule",
                    Map.of(
                            "enabled",
                            true,
                            "timezone",
                            "UTC",
                            "days_of_week",
                            List.of(1, 2, 3, 4, 5, 6, 7),
                            "hours_from",
                            0,
                            "hours_to",
                            24)));
            assertThat(CrosspostFilterEngine.evaluate(base().build(), config, Instant.parse("2026-11-19T12:00:00Z")))
                    .isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://t.me/x", "www.example.com/a", "vk.cc/abc"})
    void linkPatternsDetectedWhenExcludeLinks(String link) {
        Map<String, Object> config = filters(Map.of("exclude_ads", false, "exclude_links", true));
        assertThat(CrosspostFilterEngine.firstReason(base().text("go " + link).build(), config))
                .isEqualTo("exclude_links");
    }

    @ParameterizedTest
    @CsvSource({
        "true,false,false,false,exclude_video",
        "false,true,false,false,exclude_clips",
        "false,false,true,false,exclude_poll",
        "false,false,false,true,exclude_geo"
    })
    void mediaMatrix(boolean video, boolean clip, boolean poll, boolean geo, String expected) {
        Map<String, Object> config = filters(Map.of("exclude_ads", false, "exclude_video", true));
        CrosspostCandidate c = base().hasVideo(video).hasClip(clip).hasPoll(poll).hasGeo(geo).build();
        assertThat(CrosspostFilterEngine.firstReason(c, config)).isEqualTo(expected);
    }

    @Test
    void multipleReasonsCollected() {
        Map<String, Object> config = filters(Map.of(
                "exclude_ads", false,
                "exclude_links", true,
                "skip_if_no_images", true));
        List<String> reasons = CrosspostFilterEngine.evaluate(
                base().text("https://x.test").hasImage(false).build(), config);
        assertThat(reasons).contains("exclude_links", "skip_if_no_images");
    }

    @Test
    void hashtagOnlyIsBlankText() {
        Map<String, Object> config = filters(Map.of("exclude_ads", false, "skip_if_no_text", true));
        assertThat(CrosspostFilterEngine.firstReason(base().text("#tag #news").build(), config))
                .isEqualTo("skip_if_no_text");
    }

    private static CrosspostCandidate.Builder base() {
        return CrosspostCandidate.builder().network("vk").remoteId("1").text("hello");
    }

    private static Map<String, Object> defaults() {
        return CrosspostConfigSupport.withDefaults(Map.of());
    }

    private static Map<String, Object> filters(Map<String, Object> filters) {
        Map<String, Object> config = new HashMap<>();
        config.put("filters", new HashMap<>(filters));
        return CrosspostConfigSupport.withDefaults(config);
    }
}
