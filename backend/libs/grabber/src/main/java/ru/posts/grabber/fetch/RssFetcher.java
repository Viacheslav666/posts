package ru.posts.grabber.fetch;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.posts.grabber.ssrf.SsrfGuard;
import ru.posts.grabber.ssrf.SsrfProperties;

@Component
@Profile("!openapi")
@EnableConfigurationProperties(SsrfProperties.class)
public class RssFetcher {

    private final RestClient restClient;
    private final SsrfProperties ssrfProperties;

    public RssFetcher(SsrfProperties ssrfProperties) {
        this.ssrfProperties = ssrfProperties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(SsrfGuard.DEFAULT_CONNECT_TIMEOUT_MS));
        factory.setReadTimeout(Duration.ofMillis(SsrfGuard.DEFAULT_READ_TIMEOUT_MS));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public List<SourceCandidate> fetch(String rssUrl, int maxItems) {
        SsrfGuard.validateHttpUrl(rssUrl, ssrfProperties.getAllowedHosts());
        byte[] body = restClient.get().uri(URI.create(rssUrl.trim())).retrieve().body(byte[].class);
        if (body == null || body.length == 0) {
            return List.of();
        }
        if (body.length > SsrfGuard.DEFAULT_MAX_BYTES) {
            throw new IllegalArgumentException("RSS feed exceeds size limit");
        }
        return RssFeedParser.parse(rssUrl, body, Math.max(1, Math.min(maxItems, 100)));
    }
}
