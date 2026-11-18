package ru.posts.ord.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdMarkerTest {

    @Test
    void formatBuildsMarkerLine() {
        assertThat(AdMarker.format("ООО Ромашка", "7707083893", "2VtzqvwkhtU"))
                .isEqualTo("Реклама. ООО Ромашка. ИНН 7707083893. erid: 2VtzqvwkhtU");
    }

    @Test
    void prependJoinsWithNewline() {
        assertThat(AdMarker.prepend("Текст поста", "Реклама. X. ИНН 1. erid: e"))
                .isEqualTo("Реклама. X. ИНН 1. erid: e\nТекст поста");
        assertThat(AdMarker.prepend(null, "marker")).isEqualTo("marker");
        assertThat(AdMarker.prepend("text", null)).isEqualTo("text");
    }
}
