package ru.posts.grabber.transform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.filter.AttachmentTypes;
import ru.posts.grabber.text.TextSignals;

/** Pure content transforms: strip types, replace words, macros, attribution. */
public final class ContentTransformer {

    private ContentTransformer() {}

    public record TransformResult(String text, List<Map<String, Object>> attachments) {}

    public static String transform(String text, Map<String, Object> config) {
        return transform(text, List.of(), config, MacroExpander.Context.of("", "", ""))
                .text();
    }

    public static TransformResult transform(
            String text,
            List<Map<String, Object>> attachments,
            Map<String, Object> config,
            MacroExpander.Context macroCtx) {
        Map<String, Object> transform = GrabberConfigSupport.transform(config);
        String result = text == null ? "" : text;
        List<Map<String, Object>> atts = attachments == null ? new ArrayList<>() : new ArrayList<>(attachments);

        if (bool(transform.get("strip_all_text"))) {
            result = "";
        }
        if (bool(transform.get("strip_hashtags"))) {
            result = TextSignals.stripHashtags(result);
        }
        if (bool(transform.get("strip_mentions"))) {
            result = TextSignals.stripMentions(result);
        }
        if (bool(transform.get("strip_all_links")) || bool(transform.get("strip_links"))) {
            result = TextSignals.stripLinks(result);
        }
        if (bool(transform.get("strip_phones"))) {
            result = TextSignals.stripPhones(result);
        }

        List<String> removeKeywords = stringList(transform.get("remove_keywords"));
        for (String keyword : removeKeywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            Pattern p = Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            result = p.matcher(result).replaceAll("");
        }

        Map<String, String> replacements = stringMap(transform.get("word_replacements"));
        for (Map.Entry<String, String> e : replacements.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                continue;
            }
            Pattern p = Pattern.compile(Pattern.quote(e.getKey()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            result = p.matcher(result).replaceAll(Matcher.quoteReplacement(e.getValue() == null ? "" : e.getValue()));
        }

        Set<String> stripTypes = stripTypeSet(transform);
        if (!stripTypes.isEmpty()) {
            atts.removeIf(att -> {
                Set<String> aliases = AttachmentTypes.presentTypes(List.of(att));
                return AttachmentTypes.containsAny(aliases, stripTypes);
            });
        }
        if (bool(transform.get("strip_all_links")) || bool(transform.get("strip_links"))) {
            atts.removeIf(att -> AttachmentTypes.presentTypes(List.of(att)).contains("link"));
        }

        boolean attachOnlyWhenEmpty = bool(transform.get("attach_when_no_attachments"));
        List<Map<String, Object>> extra = objectMapList(transform.get("attached_attachments"));
        if (!extra.isEmpty() && (!attachOnlyWhenEmpty || atts.isEmpty())) {
            atts.addAll(extra);
        }

        MacroExpander.Context ctx = macroCtx == null ? MacroExpander.Context.of("", "", "") : macroCtx;
        String prepend = MacroExpander.expand(string(transform.get("prepend_text")), config, ctx);
        String append = MacroExpander.expand(string(transform.get("append_text")), config, ctx);
        if (!prepend.isBlank()) {
            result = prepend + (result.isBlank() ? "" : "\n" + result);
        }
        if (!append.isBlank()) {
            result = result + (result.isBlank() ? "" : "\n") + append;
        }

        if (bool(transform.get("link_to_source"))
                && ctx.remotePostId() != null
                && !ctx.remotePostId().isBlank()) {
            String permalink = "https://vk.com/" + ctx.remotePostId();
            if (!result.contains(permalink)) {
                result = result + (result.isBlank() ? "" : "\n") + permalink;
            }
        }
        if (bool(transform.get("link_to_author"))
                && ctx.sourceId() != null
                && !ctx.sourceId().isBlank()) {
            String author = ctx.sourceId().startsWith("-") || ctx.sourceId().matches("\\d+")
                    ? "https://vk.com/club" + ctx.sourceId().replace("-", "")
                    : "https://vk.com/" + ctx.sourceId();
            if (!result.contains(author)) {
                result = result + (result.isBlank() ? "" : "\n") + author;
            }
        }

        Object maxObj = transform.get("text_max_length");
        if (maxObj instanceof Number n && n.intValue() > 0 && result.length() > n.intValue()) {
            int max = n.intValue();
            result = result.substring(0, Math.max(0, max - 1)) + "…";
        }
        result = result.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        return new TransformResult(result, List.copyOf(atts));
    }

    /**
     * Appends the source author name as its own line. A second call with the same name does not
     * duplicate the line (the processor and the publisher both apply it).
     */
    public static String appendAuthorSignature(String text, String authorName) {
        if (authorName == null || authorName.isBlank()) {
            return text == null ? "" : text;
        }
        String name = authorName.trim();
        String body = text == null ? "" : text;
        for (String line : body.split("\\R")) {
            if (line.trim().equals(name)) {
                return body;
            }
        }
        return body.isBlank() ? name : body + "\n" + name;
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

    /** Expands the first-comment template. The publisher posts it from the community after publish. */
    public static String firstCommentDraft(Map<String, Object> config, MacroExpander.Context ctx) {
        Map<String, Object> fc = GrabberConfigSupport.firstComment(config);
        if (!bool(fc.get("enabled"))) {
            return null;
        }
        String text = string(fc.get("text"));
        if (text.isBlank()) {
            return null;
        }
        return MacroExpander.expand(text, config, ctx);
    }

    private static Set<String> stripTypeSet(Map<String, Object> transform) {
        Map<String, String> flags = Map.ofEntries(
                Map.entry("strip_photo", "photo"),
                Map.entry("strip_video", "video"),
                Map.entry("strip_clip", "clip"),
                Map.entry("strip_audio", "audio"),
                Map.entry("strip_music", "audio"),
                Map.entry("strip_playlist", "playlist"),
                Map.entry("strip_doc", "doc"),
                Map.entry("strip_file", "doc"),
                Map.entry("strip_poll", "poll"),
                Map.entry("strip_album", "album"),
                Map.entry("strip_market", "market"),
                Map.entry("strip_product", "market"),
                Map.entry("strip_market_album", "market_album"),
                Map.entry("strip_wiki", "wiki"),
                Map.entry("strip_article", "article"));
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : flags.entrySet()) {
            if (bool(transform.get(e.getKey()))) {
                out.add(e.getValue());
            }
        }
        out.addAll(stringList(transform.get("strip_attachment_types")));
        return Set.copyOf(out.stream().map(AttachmentTypes::canonicalize).toList());
    }

    private static boolean bool(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().filter(v -> v != null).map(Object::toString).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            if (k != null) {
                out.put(k.toString(), v == null ? "" : v.toString());
            }
        });
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> objectMapList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                out.add(copy);
            }
        }
        return out;
    }
}
