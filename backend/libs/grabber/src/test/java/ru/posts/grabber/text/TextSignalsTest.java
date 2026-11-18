package ru.posts.grabber.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextSignalsTest {

    @Test
    void phoneAndLinkEdgeCases() {
        assertThat(TextSignals.containsPhone("пиши в лс")).isFalse();
        assertThat(TextSignals.containsPhone("тел: 8 (900) 111-22-33")).isTrue();
        assertThat(TextSignals.containsLink("без ссылок")).isFalse();
        assertThat(TextSignals.containsLink("https://youla.ru/x")).isTrue();
        assertThat(TextSignals.stripPhones("A +79991234567 B")).isEqualTo("A B");
    }

    @Test
    void capsIgnoresShortAndEmoji() {
        assertThat(TextSignals.capsRatio("ОК 🙂", 5)).isZero();
        assertThat(TextSignals.capsRatio("АБВГДЕЖЗИЙ", 5)).isEqualTo(1.0);
    }
}
