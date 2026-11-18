package ru.posts.yookassa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class YooKassaIpValidatorTest {

    private final YooKassaIpValidator validator = new YooKassaIpValidator(List.of(
            "185.71.76.0/27",
            "185.71.77.0/27",
            "77.75.153.0/25",
            "77.75.156.11/32",
            "77.75.156.35/32",
            "77.75.154.128/25",
            "2a02:5180::/32"));

    @Test
    void allowsOfficialIpv4Ranges() {
        assertThat(validator.isAllowed("185.71.76.1")).isTrue();
        assertThat(validator.isAllowed("185.71.77.30")).isTrue();
        assertThat(validator.isAllowed("77.75.153.1")).isTrue();
        assertThat(validator.isAllowed("77.75.156.11")).isTrue();
        assertThat(validator.isAllowed("77.75.156.35")).isTrue();
        assertThat(validator.isAllowed("77.75.154.200")).isTrue();
    }

    @Test
    void rejectsOutsideRanges() {
        assertThat(validator.isAllowed("8.8.8.8")).isFalse();
        assertThat(validator.isAllowed("77.75.156.12")).isFalse();
        assertThat(validator.isAllowed("185.71.76.32")).isFalse();
    }

    @Test
    void allowsIpv6Prefix() {
        assertThat(validator.isAllowed("2a02:5180::1")).isTrue();
        assertThat(validator.isAllowed("2a02:5180:abcd::1")).isTrue();
        assertThat(validator.isAllowed("2a02:5181::1")).isFalse();
    }
}
