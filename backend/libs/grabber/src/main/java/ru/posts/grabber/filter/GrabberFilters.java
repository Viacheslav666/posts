package ru.posts.grabber.filter;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.fetch.SourceCandidate;

public final class GrabberFilters {

    private GrabberFilters() {}

    public static String skipReason(SourceCandidate candidate, Map<String, Object> config) {
        Map<String, Object> filters = GrabberConfigSupport.filters(config);
        if (bool(filters.get("exclude_ads"), true) && candidate.markedAsAds()) {
            return "exclude_ads";
        }
        if (bool(filters.get("exclude_reposts"), true) && candidate.isRepost()) {
            return "exclude_reposts";
        }
        List<String> include = stringList(filters.get("keyword_include"));
        List<String> exclude = stringList(filters.get("keyword_exclude"));
        String match = string(filters.get("keyword_match"), "any");
        String text =
                candidate.contentText() == null ? "" : candidate.contentText().toLowerCase(Locale.ROOT);
        if (!include.isEmpty() && !matchesKeywords(text, include, match)) {
            return "keyword_include";
        }
        if (!exclude.isEmpty() && matchesKeywords(text, exclude, "any")) {
            return "keyword_exclude";
        }
        boolean requireAttachments = bool(filters.get("require_attachments"), false);
        List<Map<String, Object>> attachments = candidate.attachments() == null ? List.of() : candidate.attachments();
        if (requireAttachments && attachments.isEmpty()) {
            return "require_attachments";
        }
        Set<String> allowed = stringList(filters.get("attachment_types")).stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!allowed.isEmpty() && !attachments.isEmpty()) {
            boolean anyAllowed = attachments.stream()
                    .map(a -> String.valueOf(a.getOrDefault("type", "none")).toLowerCase(Locale.ROOT))
                    .anyMatch(allowed::contains);
            if (!anyAllowed && !allowed.contains("none")) {
                return "attachment_types";
            }
        }
        return null;
    }

    private static boolean matchesKeywords(String text, Collection<String> keywords, String mode) {
        List<String> needles = keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(k -> k.toLowerCase(Locale.ROOT))
                .toList();
        if (needles.isEmpty()) {
            return true;
        }
        if ("all".equalsIgnoreCase(mode)) {
            return needles.stream().allMatch(text::contains);
        }
        if ("whole_word".equalsIgnoreCase(mode)) {
            return needles.stream()
                    .anyMatch(k -> text.matches("(?s).*\\b" + java.util.regex.Pattern.quote(k) + "\\b.*"));
        }
        return needles.stream().anyMatch(text::contains);
    }

    private static boolean bool(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().filter(v -> v != null).map(Object::toString).toList();
        }
        return List.of();
    }

    private static String string(Object raw, String defaultValue) {
        return raw == null || raw.toString().isBlank() ? defaultValue : raw.toString();
    }
}
