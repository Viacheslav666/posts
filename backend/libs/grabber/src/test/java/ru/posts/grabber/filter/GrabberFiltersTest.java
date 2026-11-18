package ru.posts.grabber.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.fetch.SourceCandidate;

/** TC-GRAB-19 — filters exclude ads/reposts/keywords. */
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
        Map<String, Object> config = GrabberConfigSupport.withDefaults(Map.of(
                "filters",
                Map.of(
                        "exclude_ads",
                        false,
                        "exclude_reposts",
                        false,
                        "keyword_include",
                        List.of("news"),
                        "keyword_exclude",
                        List.of("spam"),
                        "keyword_match",
                        "any")));
        assertThat(GrabberFilters.skipReason(candidate("breaking news today", false, false), config))
                .isNull();
        assertThat(GrabberFilters.skipReason(candidate("unrelated", false, false), config))
                .isEqualTo("keyword_include");
        assertThat(GrabberFilters.skipReason(candidate("news and spam", false, false), config))
                .isEqualTo("keyword_exclude");
    }

    private static SourceCandidate candidate(String text, boolean ads, boolean repost) {
        return new SourceCandidate(
                "vk",
                "-1",
                "wall-1_1",
                "https://vk.com/wall-1_1",
                "src",
                text,
                null,
                List.of(),
                List.of(),
                List.of(),
                ads,
                repost,
                Map.of());
    }
}
