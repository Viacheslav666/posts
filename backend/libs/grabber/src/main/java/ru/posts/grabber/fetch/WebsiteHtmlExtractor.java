package ru.posts.grabber.fetch;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import ru.posts.grabber.sanitize.HtmlSanitizer;

/** Extracts title/text/images/attribution link from HTML pages (OG + optional CSS selector). */
public final class WebsiteHtmlExtractor {

    private WebsiteHtmlExtractor() {}

    public static SourceCandidate extract(String pageUrl, byte[] body, String contentType, String cssSelector) {
        Charset charset = charsetFromContentType(contentType);
        String html = new String(body == null ? new byte[0] : body, charset);
        Document doc = Jsoup.parse(html, pageUrl == null ? "" : pageUrl);

        String title = firstNonBlank(
                meta(doc, "og:title"), meta(doc, "twitter:title"), doc.title(), textOf(doc.selectFirst("h1")));
        String permalink = firstNonBlank(meta(doc, "og:url"), canonical(doc), pageUrl);
        String siteName = firstNonBlank(meta(doc, "og:site_name"), hostOf(permalink));

        Element articleRoot = resolveArticleRoot(doc, cssSelector);
        String rawHtml = articleRoot == null ? "" : articleRoot.html();
        String sanitizedHtml = HtmlSanitizer.sanitize(rawHtml);
        String plainFromArticle = HtmlSanitizer.toPlainText(sanitizedHtml);
        String description = firstNonBlank(meta(doc, "og:description"), meta(doc, "description"));
        String contentText;
        if (plainFromArticle != null && !plainFromArticle.isBlank()) {
            contentText = joinTitleBody(title, plainFromArticle);
        } else {
            contentText = joinTitleBody(title, description);
            if (sanitizedHtml.isBlank() && description != null) {
                sanitizedHtml = HtmlSanitizer.sanitize("<p>" + description + "</p>");
            }
        }

        List<Map<String, Object>> attachments = images(doc, articleRoot, permalink);
        List<String> attachmentIds = new ArrayList<>();
        for (Map<String, Object> att : attachments) {
            Object url = att.get("url");
            if (url != null) {
                attachmentIds.add("link:" + url);
            }
        }

        String remoteId = normalizeRemoteId(permalink);
        String sourceId = sourceIdFromUrl(permalink == null ? pageUrl : permalink);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("title", title);
        raw.put("permalink", permalink);
        raw.put("siteName", siteName);
        raw.put("selector", cssSelector);

        return SourceCandidate.of(
                "website",
                sourceId,
                remoteId,
                permalink,
                siteName,
                contentText,
                sanitizedHtml,
                attachments,
                List.of(),
                attachmentIds,
                false,
                false,
                raw);
    }

    private static Element resolveArticleRoot(Document doc, String cssSelector) {
        if (cssSelector != null && !cssSelector.isBlank()) {
            try {
                Element selected = doc.selectFirst(cssSelector.trim());
                if (selected != null) {
                    return selected;
                }
            } catch (Exception ignored) {
                // invalid selector → fall through
            }
        }
        Element article = doc.selectFirst("article");
        if (article != null) {
            return article;
        }
        Element main = doc.selectFirst("main");
        if (main != null) {
            return main;
        }
        Element body = doc.body();
        return body;
    }

    private static List<Map<String, Object>> images(Document doc, Element articleRoot, String baseUrl) {
        Set<String> urls = new LinkedHashSet<>();
        String ogImage = meta(doc, "og:image");
        if (ogImage != null && !ogImage.isBlank()) {
            urls.add(absolutize(baseUrl, ogImage));
        }
        Elements imgs = articleRoot == null ? doc.select("img[src]") : articleRoot.select("img[src]");
        for (Element img : imgs) {
            String src = img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src");
            if (src == null || src.isBlank() || src.startsWith("data:")) {
                continue;
            }
            urls.add(absolutize(baseUrl, src));
            if (urls.size() >= 10) {
                break;
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "photo");
            map.put("url", url);
            out.add(map);
        }
        return out;
    }

    private static String meta(Document doc, String property) {
        Element byProp = doc.selectFirst("meta[property=" + property + "]");
        if (byProp != null && byProp.hasAttr("content")) {
            return byProp.attr("content").trim();
        }
        Element byName = doc.selectFirst("meta[name=" + property + "]");
        if (byName != null && byName.hasAttr("content")) {
            return byName.attr("content").trim();
        }
        return null;
    }

    private static String canonical(Document doc) {
        Element link = doc.selectFirst("link[rel=canonical]");
        return link == null ? null : link.attr("abs:href");
    }

    private static String textOf(Element el) {
        return el == null ? null : el.text();
    }

    private static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return "website";
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? "website" : host;
        } catch (Exception ex) {
            return "website";
        }
    }

    private static String absolutize(String base, String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        try {
            if (base == null || base.isBlank()) {
                return URI.create(url.trim()).toString();
            }
            return URI.create(base.trim()).resolve(url.trim()).toString();
        } catch (Exception ex) {
            return url.trim();
        }
    }

    private static String normalizeRemoteId(String permalink) {
        if (permalink == null || permalink.isBlank()) {
            return "unknown";
        }
        try {
            URI uri = URI.create(permalink.trim());
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String query = uri.getQuery();
            return query == null || query.isBlank()
                    ? scheme + "://" + host + path
                    : scheme + "://" + host + path + "?" + query;
        } catch (Exception ex) {
            return permalink.trim();
        }
    }

    private static String sourceIdFromUrl(String url) {
        String host = hostOf(url).toLowerCase(Locale.ROOT);
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(host.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 32);
        } catch (Exception ex) {
            return host;
        }
    }

    private static String joinTitleBody(String title, String body) {
        if (title == null || title.isBlank()) {
            return body == null ? "" : body.trim();
        }
        if (body == null || body.isBlank()) {
            return title.trim();
        }
        String plainBody = body.trim();
        if (plainBody.startsWith(title.trim())) {
            return plainBody;
        }
        return title.trim() + "\n" + plainBody;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static Charset charsetFromContentType(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("charset=");
        if (idx < 0) {
            return StandardCharsets.UTF_8;
        }
        String name = contentType.substring(idx + 8).trim();
        int semi = name.indexOf(';');
        if (semi >= 0) {
            name = name.substring(0, semi).trim();
        }
        name = name.replace("\"", "");
        try {
            return Charset.forName(name);
        } catch (Exception ex) {
            return StandardCharsets.UTF_8;
        }
    }
}
