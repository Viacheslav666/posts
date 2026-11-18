package ru.posts.grabber.fetch;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.posts.grabber.ssrf.SafeHttpFetcher;
import ru.posts.grabber.ssrf.SsrfGuard;
import ru.posts.grabber.ssrf.SsrfProperties;

@Component
@Profile("!openapi")
@EnableConfigurationProperties(SsrfProperties.class)
public class RssFetcher {

    public static final Set<String> FEED_CONTENT_TYPES = Set.of(
            "application/rss+xml",
            "application/atom+xml",
            "application/xml",
            "text/xml",
            "application/rdf+xml",
            "text/plain",
            "text/html");

    private final SafeHttpFetcher safeHttpFetcher;
    private final SsrfProperties ssrfProperties;

    public RssFetcher(SafeHttpFetcher safeHttpFetcher, SsrfProperties ssrfProperties) {
        this.safeHttpFetcher = safeHttpFetcher;
        this.ssrfProperties = ssrfProperties;
    }

    public List<SourceCandidate> fetch(String rssUrl, int maxItems) {
        SsrfGuard.validateHttpUrl(rssUrl, ssrfProperties.getAllowedHosts());
        SafeHttpFetcher.FetchResult result =
                safeHttpFetcher.fetch(rssUrl, FEED_CONTENT_TYPES, ssrfProperties.getAllowedHosts());
        String ct = result.contentType() == null ? "" : result.contentType().toLowerCase(Locale.ROOT);
        // Some feeds are served as text/html; still attempt XML parse.
        if (!ct.isBlank()
                && !FEED_CONTENT_TYPES.contains(ct)
                && !ct.contains("xml")
                && !ct.contains("rss")
                && !ct.contains("atom")) {
            throw new IllegalArgumentException("Disallowed content-type for RSS: " + ct);
        }
        return RssFeedParser.parse(result.finalUrl(), result.body(), Math.max(1, Math.min(maxItems, 100)));
    }
}
