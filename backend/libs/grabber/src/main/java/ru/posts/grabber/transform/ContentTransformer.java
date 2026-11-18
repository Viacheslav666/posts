package ru.posts.grabber.transform;

import java.util.Map;
import ru.posts.grabber.config.GrabberConfigSupport;

public final class ContentTransformer {

    private ContentTransformer() {}

    public static String transform(String text, Map<String, Object> config) {
        Map<String, Object> transform = GrabberConfigSupport.transform(config);
        String result = text == null ? "" : text;
        if (bool(transform.get("strip_hashtags"))) {
            result = result.replaceAll("(?u)#[\\p{L}\\p{N}_]+", "").trim();
        }
        if (bool(transform.get("strip_mentions"))) {
            result = result.replaceAll("(?u)@[\\p{L}\\p{N}._]+", "").trim();
        }
        String prepend = string(transform.get("prepend_text"));
        String append = string(transform.get("append_text"));
        if (!prepend.isBlank()) {
            result = prepend + (result.isBlank() ? "" : "\n" + result);
        }
        if (!append.isBlank()) {
            result = result + (result.isBlank() ? "" : "\n") + append;
        }
        Object maxObj = transform.get("text_max_length");
        if (maxObj instanceof Number n && n.intValue() > 0 && result.length() > n.intValue()) {
            int max = n.intValue();
            result = result.substring(0, Math.max(0, max - 1)) + "…";
        }
        return result.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    public static String withAttribution(String text, Map<String, Object> config, String sourceName, String sourceUrl) {
        if (!GrabberConfigSupport.attributionEnabled(config)) {
            return text;
        }
        String template = GrabberConfigSupport.attributionTemplate(config);
        String footer = template.replace("{source_name}", sourceName == null ? "" : sourceName)
                .replace("{source_url}", sourceUrl == null ? "" : sourceUrl)
                .replace("{author}", sourceName == null ? "" : sourceName);
        if (footer.isBlank()) {
            return text;
        }
        if (text == null || text.isBlank()) {
            return footer;
        }
        return text + "\n\n" + footer;
    }

    private static boolean bool(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
