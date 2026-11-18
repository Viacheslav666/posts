package ru.posts.grabber.fetch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.posts.grabber.ssrf.SafeHttpFetcher;
import ru.posts.grabber.ssrf.SsrfBlockedException;
import ru.posts.grabber.ssrf.SsrfGuard;
import ru.posts.grabber.ssrf.SsrfProperties;

@Component
@Profile("!openapi")
public class WebsiteFetcher {

    public static final Set<String> HTML_CONTENT_TYPES =
            Set.of("text/html", "application/xhtml+xml", "application/xml", "text/xml", "text/plain");

    private final SafeHttpFetcher safeHttpFetcher;
    private final SsrfProperties ssrfProperties;

    public WebsiteFetcher(SafeHttpFetcher safeHttpFetcher, SsrfProperties ssrfProperties) {
        this.safeHttpFetcher = safeHttpFetcher;
        this.ssrfProperties = ssrfProperties;
    }

    public List<SourceCandidate> fetch(List<String> urls, String cssSelector) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String url : urls) {
            if (url != null && !url.isBlank()) {
                unique.add(url.trim());
            }
        }
        if (unique.size() > 20) {
            throw new IllegalArgumentException("website_urls must contain at most 20 URLs");
        }
        List<SourceCandidate> out = new ArrayList<>();
        for (String url : unique) {
            SsrfGuard.validateHttpUrl(url, ssrfProperties.getAllowedHosts());
            SafeHttpFetcher.FetchResult result =
                    safeHttpFetcher.fetch(url, HTML_CONTENT_TYPES, ssrfProperties.getAllowedHosts());
            out.add(WebsiteHtmlExtractor.extract(result.finalUrl(), result.body(), result.contentType(), cssSelector));
        }
        return out;
    }

    public void validateUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("website_urls is required");
        }
        if (urls.size() > 20) {
            throw new IllegalArgumentException("website_urls must contain at most 20 URLs");
        }
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("website_urls entries must be non-blank");
            }
            String trimmed = url.trim().toLowerCase(Locale.ROOT);
            if (!(trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
                throw new SsrfBlockedException("Only http(s) URLs are allowed");
            }
            SsrfGuard.validateHttpUrl(url, ssrfProperties.getAllowedHosts());
        }
    }
}
