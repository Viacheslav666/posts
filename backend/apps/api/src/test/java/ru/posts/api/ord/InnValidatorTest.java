package ru.posts.api.ord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InnValidatorTest {

    @Test
    void acceptsTenDigitInn() {
        assertThat(InnValidator.isValid("7707083893")).isTrue();
    }

    @Test
    void acceptsTwelveDigitInn() {
        assertThat(InnValidator.isValid("500100732259")).isTrue();
    }

    @Test
    void rejectsWrongLength() {
        assertThat(InnValidator.isValid("123")).isFalse();
        assertThat(InnValidator.isValid("12345678901")).isFalse();
    }

    @Test
    void rejectsNonDigits() {
        assertThat(InnValidator.isValid("770708389X")).isFalse();
        assertThat(InnValidator.isValid(null)).isFalse();
        assertThat(InnValidator.isValid("")).isFalse();
    }

    @Test
    void rejectsInvalidChecksum() {
        assertThat(InnValidator.isValid("7707083890")).isFalse();
        assertThat(InnValidator.isValid("500100732258")).isFalse();
    }

    @Test
    void foreignAcceptsNonRuTaxId() {
        assertThat(InnValidator.isValid("DE123456789", true)).isTrue();
        assertThat(InnValidator.isValid("", true)).isFalse();
    }

    @Test
    void requireValidThrows() {
        assertThatThrownBy(() -> InnValidator.requireValid("abc")).isInstanceOf(IllegalArgumentException.class);
    }
}
