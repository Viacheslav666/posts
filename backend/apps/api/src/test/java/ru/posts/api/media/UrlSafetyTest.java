package ru.posts.api.media;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UrlSafetyTest {

    @Test
    void rejectsLocalhost() {
        assertThatThrownBy(() -> UrlSafety.validateHttpUrl("http://127.0.0.1/x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlSafety.validateHttpUrl("http://localhost/x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMetadataIp() {
        assertThatThrownBy(() -> UrlSafety.validateHttpUrl("http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFileScheme() {
        assertThatThrownBy(() -> UrlSafety.validateHttpUrl("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
