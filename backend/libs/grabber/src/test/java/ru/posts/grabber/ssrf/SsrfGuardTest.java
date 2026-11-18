package ru.posts.grabber.ssrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("file:///etc/passwd"))
                .isInstanceOf(SsrfBlockedException.class);
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("gopher://example.com/1"))
                .isInstanceOf(SsrfBlockedException.class);
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("http://[::1]/")).isInstanceOf(SsrfBlockedException.class);
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("http://[::ffff:127.0.0.1]/"))
                .isInstanceOf(SsrfBlockedException.class);
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("http://[fe80::1]/"))
                .isInstanceOf(SsrfBlockedException.class);
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("http://[fc00::1]/"))
                .isInstanceOf(SsrfBlockedException.class);
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("http://10.0.0.5/x"))
                .isInstanceOf(SsrfBlockedException.class);
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("http://172.16.0.1/x"))
                .isInstanceOf(SsrfBlockedException.class);
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("http://192.168.1.1/x"))
                .isInstanceOf(SsrfBlockedException.class);
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("http://100.64.1.2/x"))
                .isInstanceOf(SsrfBlockedException.class);
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("http://metadata.google.internal/"))
                .isInstanceOf(SsrfBlockedException.class);
    }

    @Test
    void allowsPublicHostWhenDnsSafe() throws Exception {
        HostResolver publicOnly = host -> new InetAddress[] {InetAddress.getByName("8.8.8.8")};
        assertThat(SsrfGuard.validateHttpUrl("https://feeds.example.com/rss.xml", List.of(), publicOnly)
                        .getHost())
                .isEqualTo("feeds.example.com");
    }

    @Test
    void blocksDnsRebindingWhenResolveReturnsPrivate() {
        AtomicInteger calls = new AtomicInteger();
        HostResolver rebinding = host -> {
            int n = calls.incrementAndGet();
            if (n == 1) {
                return new InetAddress[] {InetAddress.getByName("8.8.8.8")};
            }
            return new InetAddress[] {InetAddress.getByName("169.254.169.254")};
        };
        SsrfGuard.validateHttpUrl("https://evil.example/page", List.of(), rebinding);
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("https://evil.example/page", List.of(), rebinding))
                .isInstanceOf(SsrfBlockedException.class)
                .hasMessageContaining("private/reserved");
    }

    @Test
    void allowedHostsBypassPrivateCheck() throws UnknownHostException {
        SsrfGuard.validateHttpUrl("http://127.0.0.1/feed", List.of("127.0.0.1"));
        SsrfGuard.validateHttpUrl("http://localhost/feed", List.of("localhost"));
    }
}
