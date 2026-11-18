package ru.posts.grabber.ssrf;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SsrfGuardTest {

    @Test
    void blocksLoopbackAndMetadata() {
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("http://127.0.0.1/feed.xml"))
                .isInstanceOf(SsrfBlockedException.class);
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("http://localhost/feed"))
                .isInstanceOf(SsrfBlockedException.class);
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(SsrfBlockedException.class);
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("ftp://example.com/feed"))
                .isInstanceOf(SsrfBlockedException.class);
    }
}
