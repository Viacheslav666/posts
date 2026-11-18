package ru.posts.ord.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdMarkerTest {

    @Test
    void formatBuildsMarkerLine() {
        assertThat(AdMarker.format("ООО Ромашка", "7707083893", "test-erid-marker-0001"))
                .isEqualTo("Реклама. ООО Ромашка. ИНН 7707083893. erid: test-erid-marker-0001");
    }

    @Test
    void prependJoinsWithNewline() {
        assertThat(AdMarker.prepend("Текст поста", "Реклама. X. ИНН 1. erid: e"))
                .isEqualTo("Реклама. X. ИНН 1. erid: e\nТекст поста");
        assertThat(AdMarker.prepend(null, "marker")).isEqualTo("marker");
        assertThat(AdMarker.prepend("text", null)).isEqualTo("text");
    }

    @Test
    void appendJoinsWithNewline() {
        assertThat(AdMarker.append("Текст", "MARKER")).isEqualTo("Текст\nMARKER");
    }

    @Test
    void sixPlacementModes() {
        String marker = AdMarker.format("ООО", "7707083893", "erid-1");
        String body = "Текст поста https://example.com/offer";

        assertThat(AdMarker.apply(body, marker, "erid-1", MarkingPlacement.text_start, "tg", null)
                        .text())
                .startsWith(marker)
                .endsWith("https://example.com/offer");

        assertThat(AdMarker.apply(body, marker, "erid-1", MarkingPlacement.text_end, "tg", null)
                        .text())
                .endsWith(marker);

        AdMarker.ApplyResult menu = AdMarker.apply(body, marker, "erid-1", MarkingPlacement.vk_menu, "vk", null);
        assertThat(menu.text()).endsWith(marker);
        assertThat(menu.warnings()).anyMatch(w -> w.contains("vk_menu_unsupported"));
        assertThat(menu.meta()).containsEntry("vkMarkAsAds", true);

        AdMarker.ApplyResult menuEnd =
                AdMarker.apply(body, marker, "erid-1", MarkingPlacement.vk_menu_and_text_end, "vk", null);
        assertThat(menuEnd.text()).endsWith(marker);
        assertThat(menuEnd.warnings()).isNotEmpty();

        AdMarker.ApplyResult linkStart =
                AdMarker.apply(body, marker, "erid-1", MarkingPlacement.click_link_start, "tg", null);
        assertThat(linkStart.text()).contains("erid=erid-1");
        assertThat(linkStart.text()).doesNotContain("Реклама.");

        AdMarker.ApplyResult linkEndNoUrl =
                AdMarker.apply("без ссылок", marker, "erid-1", MarkingPlacement.click_link_end, "tg", null);
        assertThat(linkEndNoUrl.text()).endsWith(marker);
        assertThat(linkEndNoUrl.warnings()).anyMatch(w -> w.contains("click_link_fallback"));
    }

    @Test
    void legacyMarkingTypeMapsToPlacement() {
        assertThat(MarkingPlacement.resolve(null, "0")).isEqualTo(MarkingPlacement.vk_menu);
        assertThat(MarkingPlacement.resolve(null, "2")).isEqualTo(MarkingPlacement.text_end);
        assertThat(MarkingPlacement.resolve("text_start", "0")).isEqualTo(MarkingPlacement.text_start);
    }

    @Test
    void storyAppliesEridToLink() {
        String marker = AdMarker.format("ООО", "7707083893", "erid-story");
        AdMarker.ApplyResult result = AdMarker.applyForStory(
                "История", marker, "erid-story", MarkingPlacement.text_end, "https://brand.example/land");
        assertThat(result.linkUrl()).isEqualTo("https://brand.example/land?erid=erid-story");
        assertThat(result.text()).contains("erid: erid-story");
    }
}
