package ru.posts.grabber.hash;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimHasherTest {

    @Test
    void nearDuplicatesAbove80PercentAreDetected() {
        String a = "Сегодня в Москве прошёл сильный дождь, улицы затопило за час.";
        String b = "Сегодня в Москве прошёл сильный дождь, улицы затопило за час!";
        long fa = SimHasher.fingerprint(a);
        long fb = SimHasher.fingerprint(b);
        assertThat(SimHasher.similarity(fa, fb)).isGreaterThanOrEqualTo(0.80);
        assertThat(SimHasher.similarEnough(fa, fb, 0.80)).isTrue();
    }

    @Test
    void distantTextsBelow60PercentPass() {
        String a = "Рецепт борща: свёкла, капуста, картофель и сметана.";
        String b = "Курс доллара обновил максимум на торгах Московской биржи.";
        long fa = SimHasher.fingerprint(a);
        long fb = SimHasher.fingerprint(b);
        assertThat(SimHasher.similarity(fa, fb)).isLessThanOrEqualTo(0.60);
        assertThat(SimHasher.similarEnough(fa, fb, 0.80)).isFalse();
    }

    @Test
    void emojiAndWhitespaceNormalized() {
        long a = SimHasher.fingerprint("Привет мир 🙂");
        long b = SimHasher.fingerprint("  привет   мир  ");
        assertThat(SimHasher.similarEnough(a, b, 0.90)).isTrue();
    }

    @Test
    void emptyFingerprintStable() {
        assertThat(SimHasher.fingerprint(null)).isZero();
        assertThat(SimHasher.fingerprint("   ")).isZero();
        assertThat(SimHasher.similarity(0, 0)).isEqualTo(1.0);
    }
}
