package ru.posts.grabber.sanitize;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/** Sanitizes HTML from website/RSS extracts so post content cannot carry XSS payloads. */
public final class HtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("figure", "figcaption")
            .addAttributes("a", "rel", "target")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https");

    private HtmlSanitizer() {}

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document.OutputSettings settings = new Document.OutputSettings().prettyPrint(false);
        return Jsoup.clean(html, "", SAFELIST, settings);
    }

    public static String toPlainText(String htmlOrText) {
        if (htmlOrText == null || htmlOrText.isBlank()) {
            return "";
        }
        return Jsoup.parse(htmlOrText).text().replaceAll("\\s+", " ").trim();
    }
}
