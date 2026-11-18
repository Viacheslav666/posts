package ru.posts.grabber.ssrf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * HTTP GET with SSRF guards: scheme/host/DNS checks before connect, re-validation on every redirect
 * (DNS rebinding), size/time/redirect limits, optional content-type allow-list.
 */
@Component
@Profile("!openapi")
public class SafeHttpFetcher {

    private final SsrfProperties properties;
    private final HostResolver hostResolver;
    private final HttpClient httpClient;

    public SafeHttpFetcher(SsrfProperties properties, HostResolver hostResolver) {
        this.properties = properties;
        this.hostResolver = hostResolver == null ? HostResolver.SYSTEM : hostResolver;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(Math.max(100, properties.getConnectTimeoutMs())))
                .build();
    }

    public FetchResult fetch(String rawUrl, Set<String> allowedContentTypes) {
        return fetch(rawUrl, allowedContentTypes, properties.getAllowedHosts());
    }

    public FetchResult fetch(String rawUrl, Set<String> allowedContentTypes, Collection<String> allowedHosts) {
        URI current = SsrfGuard.validateHttpUrl(rawUrl, allowedHosts, hostResolver);
        int redirects = 0;
        int maxRedirects = Math.max(0, properties.getMaxRedirects());
        int maxBytes = Math.max(1, properties.getMaxBytes());
        Duration readTimeout = Duration.ofMillis(Math.max(100, properties.getReadTimeoutMs()));

        while (true) {
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(readTimeout)
                    .header("User-Agent", "PostsGrabber/1.0")
                    .header("Accept", "*/*")
                    .GET()
                    .build();
            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (IOException ex) {
                throw new IllegalArgumentException("Failed to fetch URL: " + current, ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalArgumentException("Fetch interrupted: " + current, ex);
            }

            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location").orElse(null);
                drainQuietly(response.body());
                if (location == null || location.isBlank()) {
                    throw new SsrfBlockedException("Redirect without Location header");
                }
                if (redirects >= maxRedirects) {
                    throw new SsrfBlockedException("Too many redirects (max " + maxRedirects + ")");
                }
                URI next = current.resolve(location.trim());
                // Re-validate every hop (DNS rebinding / redirect to metadata).
                current = SsrfGuard.validateUri(next, allowedHosts, hostResolver);
                redirects++;
                continue;
            }

            if (status < 200 || status >= 300) {
                drainQuietly(response.body());
                throw new IllegalArgumentException("HTTP " + status + " fetching " + current);
            }

            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .map(v -> v.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))
                    .orElse("");
            if (allowedContentTypes != null
                    && !allowedContentTypes.isEmpty()
                    && !contentTypeAllowed(contentType, allowedContentTypes)) {
                drainQuietly(response.body());
                throw new IllegalArgumentException("Disallowed content-type: " + contentType);
            }

            Long contentLength =
                    response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength > maxBytes) {
                drainQuietly(response.body());
                throw new IllegalArgumentException("Response exceeds size limit");
            }
            byte[] body = readLimited(response.body(), maxBytes);
            return new FetchResult(current.toString(), contentType, body, redirects);
        }
    }

    private static boolean contentTypeAllowed(String contentType, Set<String> allowed) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        for (String a : allowed) {
            if (a == null) {
                continue;
            }
            String needle = a.toLowerCase(Locale.ROOT);
            if (contentType.equals(needle) || contentType.startsWith(needle)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readLimited(InputStream in, int maxBytes) {
        try (InputStream stream = in;
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = stream.read(buf)) >= 0) {
                total += n;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("Response exceeds size limit");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read response body", ex);
        }
    }

    private static void drainQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try (InputStream stream = in) {
            stream.transferTo(OutputNull.INSTANCE);
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private static final class OutputNull extends java.io.OutputStream {
        static final OutputNull INSTANCE = new OutputNull();

        @Override
        public void write(int b) {}

        @Override
        public void write(byte[] b, int off, int len) {}
    }

    public static final class FetchResult {
        private final String finalUrl;
        private final String contentType;
        private final byte[] body;
        private final int redirectCount;

        public FetchResult(String finalUrl, String contentType, byte[] body, int redirectCount) {
            this.finalUrl = Objects.requireNonNull(finalUrl, "finalUrl");
            this.contentType = contentType;
            this.body = body == null ? new byte[0] : body;
            this.redirectCount = redirectCount;
        }

        public String finalUrl() {
            return finalUrl;
        }

        public String contentType() {
            return contentType;
        }

        public byte[] body() {
            return body;
        }

        public int redirectCount() {
            return redirectCount;
        }
    }
}
