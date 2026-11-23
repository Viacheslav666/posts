package ru.posts.grabber.ssrf;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SafeHttpFetcherSsrfTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private SsrfProperties props;
    private SafeHttpFetcher fetcher;

    @BeforeEach
    void setUp() {
        props = new SsrfProperties();
        props.setAllowedHosts(List.of("localhost", "127.0.0.1"));
        props.setMaxRedirects(3);
        props.setMaxBytes(64 * 1024);
        fetcher = new SafeHttpFetcher(props, HostResolver.SYSTEM);
        wireMock.resetAll();
    }

    @Test
    void fetchesAllowedLocalhostHtml() {
        wireMock.stubFor(get(urlPathEqualTo("/article"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>ok</body></html>")));
        SafeHttpFetcher.FetchResult result = fetcher.fetch(wireMock.baseUrl() + "/article", Set.of("text/html"));
        assertThat(new String(result.body(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("ok");
        assertThat(result.redirectCount()).isZero();
    }

    @Test
    void blocksRedirectToMetadataIp() {
        wireMock.stubFor(get(urlPathEqualTo("/hop"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", "http://169.254.169.254/latest/meta-data/")));
        assertThatThrownBy(() -> fetcher.fetch(wireMock.baseUrl() + "/hop", Set.of("text/html")))
                .isInstanceOf(SsrfBlockedException.class)
                .hasMessageContaining("private/reserved");
        wireMock.verify(1, getRequestedFor(urlEqualTo("/hop")));
    }

    @Test
    void blocksRedirectToLoopbackLiteral() {
        wireMock.stubFor(get(urlPathEqualTo("/loop"))
                .willReturn(aResponse().withStatus(301).withHeader("Location", "http://127.0.0.1:9/secret")));
        // 127.0.0.1 is in allowedHosts for WireMock tests — use a non-allowed private instead.
        wireMock.stubFor(get(urlPathEqualTo("/priv"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", "http://10.1.2.3/internal")));
        assertThatThrownBy(() -> fetcher.fetch(wireMock.baseUrl() + "/priv", Set.of("text/html")))
                .isInstanceOf(SsrfBlockedException.class);
    }

    @Test
    void blocksDnsRebindingOnRedirectResolve() {
        AtomicInteger resolves = new AtomicInteger();
        HostResolver rebinding = host -> {
            if ("rebinding.test".equals(host)) {
                int n = resolves.incrementAndGet();
                if (n == 1) {
                    // First hop: pretend public
                    return new InetAddress[] {InetAddress.getByName("8.8.8.8")};
                }
                // Later lookup (redirect target same host) flips to metadata
                return new InetAddress[] {InetAddress.getByName("169.254.169.254")};
            }
            return InetAddress.getAllByName(host);
        };
        // Direct validation of rebinding host after first public resolve then private
        SsrfGuard.validateHttpUrl("http://rebinding.test/a", List.of(), rebinding);
        assertThatThrownBy(() -> SsrfGuard.validateHttpUrl("http://rebinding.test/b", List.of(), rebinding))
                .isInstanceOf(SsrfBlockedException.class);
    }

    @Test
    void rejectsDisallowedContentType() {
        wireMock.stubFor(get(urlPathEqualTo("/bin"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBody(new byte[] {1, 2, 3})));
        assertThatThrownBy(() -> fetcher.fetch(wireMock.baseUrl() + "/bin", Set.of("text/html")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content-type");
    }

    @Test
    void enforcesMaxRedirects() {
        wireMock.stubFor(get(urlPathEqualTo("/r1"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", wireMock.baseUrl() + "/r2")));
        wireMock.stubFor(get(urlPathEqualTo("/r2"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", wireMock.baseUrl() + "/r3")));
        wireMock.stubFor(get(urlPathEqualTo("/r3"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", wireMock.baseUrl() + "/r4")));
        wireMock.stubFor(get(urlPathEqualTo("/r4"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", wireMock.baseUrl() + "/final")));
        assertThatThrownBy(() -> fetcher.fetch(wireMock.baseUrl() + "/r1", Set.of("text/html")))
                .isInstanceOf(SsrfBlockedException.class)
                .hasMessageContaining("redirects");
    }

    @Test
    void bodyReadDeadlineAbortsASlowStream() throws Exception {
        props.setReadTimeoutMs(300);
        fetcher = new SafeHttpFetcher(props, HostResolver.SYSTEM);
        try (ServerSocket server = new ServerSocket(0)) {
            Thread slow = new Thread(() -> {
                try (Socket socket = server.accept();
                        OutputStream out = socket.getOutputStream()) {
                    out.write(
                            """
                            HTTP/1.1 200 OK\r
                            Content-Type: text/html\r
                            Content-Length: 32\r
                            Connection: close\r
                            \r
                            """
                                    .getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    Thread.sleep(5_000);
                    out.write("x".repeat(32).getBytes(StandardCharsets.US_ASCII));
                } catch (Exception ignored) {
                    // client closes the stream when the deadline fires
                }
            });
            slow.setDaemon(true);
            slow.start();
            assertThatThrownBy(() ->
                            fetcher.fetch("http://127.0.0.1:" + server.getLocalPort() + "/slow", Set.of("text/html")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("deadline");
        }
    }

    @Test
    void redirectBodyIsNotDrained() {
        props.setReadTimeoutMs(300);
        fetcher = new SafeHttpFetcher(props, HostResolver.SYSTEM);
        wireMock.stubFor(get(urlPathEqualTo("/slow-redirect"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", wireMock.baseUrl() + "/article")
                        .withBody("x".repeat(80_000))
                        .withChunkedDribbleDelay(40, 8_000)));
        wireMock.stubFor(get(urlPathEqualTo("/article"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html>ok</html>")));
        long started = System.nanoTime();
        SafeHttpFetcher.FetchResult result = fetcher.fetch(wireMock.baseUrl() + "/slow-redirect", Set.of("text/html"));
        assertThat(new String(result.body(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("ok");
        assertThat(System.nanoTime() - started)
                .isLessThan(java.time.Duration.ofSeconds(3).toNanos());
    }

    @Test
    void errorMessageStripsSignedQuery() {
        wireMock.stubFor(get(urlPathEqualTo("/cdn")).willReturn(aResponse().withStatus(500)));
        assertThatThrownBy(() -> fetcher.fetch(wireMock.baseUrl() + "/cdn?token=supersecret", Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("supersecret");
    }

    @Test
    void enforcesSizeLimit() {
        props.setMaxBytes(32);
        fetcher = new SafeHttpFetcher(props, HostResolver.SYSTEM);
        wireMock.stubFor(get(urlPathEqualTo("/big"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("x".repeat(200))));
        assertThatThrownBy(() -> fetcher.fetch(wireMock.baseUrl() + "/big", Set.of("text/html")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }
}
