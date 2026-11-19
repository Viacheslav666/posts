package ru.posts.crosspost.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CrosspostTextSignalsTest {

    @Test
    void blankAndHashtags() {
        assertThat(CrosspostTextSignals.isBlankText(null)).isTrue();
        assertThat(CrosspostTextSignals.isBlankText("")).isTrue();
        assertThat(CrosspostTextSignals.isBlankText("   ")).isTrue();
        assertThat(CrosspostTextSignals.isBlankText("#a #b")).isTrue();
        assertThat(CrosspostTextSignals.isBlankText("hi")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://a.com", "www.x.ru/y", "t.me/chan/1", "max.ru/u"})
    void containsLink(String url) {
        assertThat(CrosspostTextSignals.containsLink("see " + url)).isTrue();
    }

    @Test
    void noLink() {
        assertThat(CrosspostTextSignals.containsLink("просто текст")).isFalse();
    }

    @Test
    void eridAndAdMark() {
        assertThat(CrosspostTextSignals.containsErid("erid=XYZ")).isTrue();
        assertThat(CrosspostTextSignals.containsAdMark("Реклама")).isTrue();
        assertThat(CrosspostTextSignals.looksLikeAd("ok", false, false)).isFalse();
        assertThat(CrosspostTextSignals.looksLikeAd("x", true, false)).isTrue();
    }

    @Test
    void removeLinksHashtags() {
        assertThat(CrosspostTextSignals.removeLinks("a https://x.com b")).isEqualTo("a b");
        assertThat(CrosspostTextSignals.removeHashtags("a #tag b")).isEqualTo("a b");
    }

    @Test
    void truncateReadMore() {
        String out = CrosspostTextSignals.truncateWithReadMore("abcdefghij", 8, "https://src");
        assertThat(out).contains("Читать полностью").contains("https://src");
    }
}
