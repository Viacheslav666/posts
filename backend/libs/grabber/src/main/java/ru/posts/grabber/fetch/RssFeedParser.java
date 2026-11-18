package ru.posts.grabber.fetch;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Minimal RSS 1.0 / 2.0 / Atom parser for BE-17. */
public final class RssFeedParser {

    private RssFeedParser() {}

    public static List<SourceCandidate> parse(String feedUrl, byte[] body, int maxItems) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
            Element root = doc.getDocumentElement();
            String local = root.getLocalName() == null ? root.getTagName() : root.getLocalName();
            List<SourceCandidate> items = new ArrayList<>();
            if ("feed".equalsIgnoreCase(local)) {
                parseAtom(feedUrl, root, items, maxItems);
            } else if ("rdf".equalsIgnoreCase(local) || "RDF".equals(local)) {
                parseRss1(feedUrl, root, items, maxItems);
            } else {
                parseRss2(feedUrl, root, items, maxItems);
            }
            return items;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse RSS/Atom feed", ex);
        }
    }

    private static void parseRss2(String feedUrl, Element root, List<SourceCandidate> out, int maxItems) {
        NodeList channels = root.getElementsByTagName("channel");
        Element channel = channels.getLength() == 0 ? root : (Element) channels.item(0);
        String channelTitle = text(channel, "title");
        NodeList items = channel.getElementsByTagName("item");
        for (int i = 0; i < items.getLength() && out.size() < maxItems; i++) {
            Element item = (Element) items.item(i);
            String guid = firstNonBlank(text(item, "guid"), text(item, "link"), text(item, "title"));
            String title = text(item, "title");
            String description = firstNonBlank(text(item, "description"), text(item, "content:encoded"));
            String link = firstNonBlank(text(item, "link"), guid);
            List<Map<String, Object>> attachments = enclosures(item);
            out.add(candidate(feedUrl, channelTitle, guid, link, title, description, attachments));
        }
    }

    private static void parseRss1(String feedUrl, Element root, List<SourceCandidate> out, int maxItems) {
        NodeList items = root.getElementsByTagNameNS("*", "item");
        if (items.getLength() == 0) {
            items = root.getElementsByTagName("item");
        }
        for (int i = 0; i < items.getLength() && out.size() < maxItems; i++) {
            Element item = (Element) items.item(i);
            String about = item.getAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "about");
            String guid = firstNonBlank(about, text(item, "link"), text(item, "title"));
            out.add(candidate(
                    feedUrl,
                    text(root, "title"),
                    guid,
                    firstNonBlank(text(item, "link"), guid),
                    text(item, "title"),
                    text(item, "description"),
                    List.of()));
        }
    }

    private static void parseAtom(String feedUrl, Element root, List<SourceCandidate> out, int maxItems) {
        String feedTitle = text(root, "title");
        NodeList entries = root.getElementsByTagNameNS("*", "entry");
        if (entries.getLength() == 0) {
            entries = root.getElementsByTagName("entry");
        }
        for (int i = 0; i < entries.getLength() && out.size() < maxItems; i++) {
            Element entry = (Element) entries.item(i);
            String id = firstNonBlank(text(entry, "id"), atomLink(entry), text(entry, "title"));
            String summary = firstNonBlank(text(entry, "summary"), text(entry, "content"));
            out.add(candidate(feedUrl, feedTitle, id, atomLink(entry), text(entry, "title"), summary, List.of()));
        }
    }

    private static SourceCandidate candidate(
            String feedUrl,
            String sourceName,
            String remoteId,
            String permalink,
            String title,
            String body,
            List<Map<String, Object>> attachments) {
        String text = joinTitleBody(title, body);
        String sourceId = feedSourceId(feedUrl);
        List<String> attachmentIds = new ArrayList<>();
        for (Map<String, Object> att : attachments) {
            Object url = att.get("url");
            if (url != null) {
                attachmentIds.add("link:" + url);
            }
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("title", title);
        raw.put("body", body);
        raw.put("permalink", permalink);
        return new SourceCandidate(
                "rss",
                sourceId,
                remoteId == null ? ContentHasherFallback(text) : remoteId,
                permalink,
                sourceName == null || sourceName.isBlank() ? feedUrl : sourceName,
                text,
                body,
                attachments,
                List.of(),
                attachmentIds,
                false,
                false,
                raw);
    }

    private static String ContentHasherFallback(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private static String feedSourceId(String feedUrl) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(feedUrl.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 32);
        } catch (Exception ex) {
            return feedUrl;
        }
    }

    private static List<Map<String, Object>> enclosures(Element item) {
        List<Map<String, Object>> out = new ArrayList<>();
        NodeList nodes = item.getElementsByTagName("enclosure");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element enc = (Element) nodes.item(i);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", classifyEnclosure(enc.getAttribute("type")));
            map.put("url", enc.getAttribute("url"));
            map.put("mime", enc.getAttribute("type"));
            out.add(map);
        }
        return out;
    }

    private static String classifyEnclosure(String mime) {
        if (mime == null) {
            return "link";
        }
        String m = mime.toLowerCase(Locale.ROOT);
        if (m.startsWith("image/")) {
            return "photo";
        }
        if (m.startsWith("video/")) {
            return "video";
        }
        if (m.startsWith("audio/")) {
            return "audio";
        }
        return "doc";
    }

    private static String atomLink(Element entry) {
        NodeList links = entry.getElementsByTagNameNS("*", "link");
        if (links.getLength() == 0) {
            links = entry.getElementsByTagName("link");
        }
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            String rel = link.getAttribute("rel");
            if (rel.isBlank() || "alternate".equalsIgnoreCase(rel)) {
                String href = link.getAttribute("href");
                if (!href.isBlank()) {
                    return href;
                }
            }
        }
        return null;
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            nodes = parent.getElementsByTagNameNS("*", tag.contains(":") ? tag.substring(tag.indexOf(':') + 1) : tag);
        }
        if (nodes.getLength() == 0) {
            return null;
        }
        Node node = nodes.item(0);
        String value = node.getTextContent();
        return value == null ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String joinTitleBody(String title, String body) {
        if (title == null || title.isBlank()) {
            return body == null ? "" : stripHtml(body);
        }
        if (body == null || body.isBlank()) {
            return title;
        }
        return title + "\n" + stripHtml(body);
    }

    private static String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }
}
