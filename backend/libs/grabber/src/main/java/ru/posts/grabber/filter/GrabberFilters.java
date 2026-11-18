package ru.posts.grabber.filter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.fetch.SourceCandidate;
import ru.posts.grabber.schedule.WorkingWindow;
import ru.posts.grabber.text.TextSignals;

/**
 * Pure skip-filters for grabber candidates. Returns a stable machine reason code or {@code null}.
 */
public final class GrabberFilters {

    private GrabberFilters() {}

    public static String skipReason(SourceCandidate candidate, Map<String, Object> config) {
        return skipReason(candidate, config, Instant.now());
    }

    public static String skipReason(SourceCandidate candidate, Map<String, Object> config, Instant now) {
        if (candidate == null) {
            return "empty_candidate";
        }
        String window = WorkingWindow.skipReasonIfClosed(config, now);
        if (window != null) {
            return window;
        }

        Map<String, Object> filters = GrabberConfigSupport.filters(config);
        String text = candidate.contentText() == null ? "" : candidate.contentText();
        String textLower = text.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> attachments = candidate.attachments() == null ? List.of() : candidate.attachments();
        Set<String> presentTypes = AttachmentTypes.presentTypes(attachments);

        Integer maxAgeDays = intOrNull(filters.get("max_age_days"));
        if (maxAgeDays != null && maxAgeDays > 0 && candidate.publishedAt() != null) {
            Instant cutoff = now.minus(maxAgeDays, ChronoUnit.DAYS);
            if (candidate.publishedAt().isBefore(cutoff)) {
                return "max_age_days";
            }
        }

        if (bool(filters.get("exclude_ads"), true) && candidate.markedAsAds()) {
            return "exclude_ads";
        }
        if (bool(filters.get("exclude_reposts"), true) && candidate.isRepost()) {
            return "exclude_reposts";
        }
        if (bool(filters.get("only_group_posts"), false) && !candidate.fromGroup()) {
            return "only_group_posts";
        }
        if (bool(filters.get("exclude_links"), false)
                && (TextSignals.containsLink(text) || presentTypes.contains("link"))) {
            return "exclude_links";
        }
        if (bool(filters.get("skip_if_no_text"), false) && TextSignals.isBlankText(text)) {
            return "skip_if_no_text";
        }
        if (bool(filters.get("skip_if_no_media"), false) && !AttachmentTypes.hasMedia(attachments)) {
            return "skip_if_no_media";
        }
        if (bool(filters.get("skip_if_any_text"), false) && !TextSignals.isBlankText(text)) {
            return "skip_if_any_text";
        }
        if (bool(filters.get("skip_if_homoglyphs"), false) && TextSignals.hasHomoglyphSubstitution(text)) {
            return "skip_if_homoglyphs";
        }
        if (bool(filters.get("skip_if_caps"), false)) {
            double threshold = doubleVal(filters.get("caps_threshold"), 0.8d);
            int minLetters = intVal(filters.get("caps_min_letters"), 8);
            if (TextSignals.capsRatio(text, minLetters) >= threshold) {
                return "skip_if_caps";
            }
        }
        if (bool(filters.get("skip_if_phone"), false) && TextSignals.containsPhone(text)) {
            return "skip_if_phone";
        }
        if (bool(filters.get("require_phone"), false) && !TextSignals.containsPhone(text)) {
            return "require_phone";
        }
        if (bool(filters.get("require_phone_or_signature"), false)
                && !TextSignals.containsPhone(text)
                && !candidate.signed()) {
            return "require_phone_or_signature";
        }

        List<String> missingTypes = stringList(filters.get("skip_if_missing_attachment_types"));
        if (!missingTypes.isEmpty() && !AttachmentTypes.containsAll(presentTypes, missingTypes)) {
            return "skip_if_missing_attachment_types";
        }
        // Convenience aliases from vposter UI: no music/photo/video/clip/product/file/poll
        for (String alias : List.of(
                "skip_if_no_music",
                "skip_if_no_photo",
                "skip_if_no_video",
                "skip_if_no_clip",
                "skip_if_no_product",
                "skip_if_no_file",
                "skip_if_no_poll")) {
            if (bool(filters.get(alias), false)) {
                String type = alias.substring("skip_if_no_".length());
                if (!AttachmentTypes.containsAny(presentTypes, List.of(type))) {
                    return alias;
                }
            }
        }

        List<String> hasTypes = stringList(filters.get("skip_if_has_attachment_types"));
        if (!hasTypes.isEmpty() && AttachmentTypes.containsAny(presentTypes, hasTypes)) {
            return "skip_if_has_attachment_types";
        }
        for (String alias : List.of(
                "skip_if_has_article",
                "skip_if_has_video",
                "skip_if_has_clip",
                "skip_if_has_photo",
                "skip_if_has_music",
                "skip_if_has_file",
                "skip_if_has_product",
                "skip_if_has_poll")) {
            if (bool(filters.get(alias), false)) {
                String type = alias.substring("skip_if_has_".length());
                if (AttachmentTypes.containsAny(presentTypes, List.of(type))) {
                    return alias;
                }
            }
        }

        List<String> include = stringList(filters.get("keyword_include"));
        List<String> exclude = stringList(filters.get("keyword_exclude"));
        List<String> stopWords = stringList(filters.get("stop_words"));
        String match = string(filters.get("keyword_match"), "any");
        if (!include.isEmpty() && !matchesKeywords(textLower, include, match)) {
            return "keyword_include";
        }
        if (!exclude.isEmpty() && matchesKeywords(textLower, exclude, "any")) {
            return "keyword_exclude";
        }
        // stop-words: occurrence match (substring), same as vposter
        if (!stopWords.isEmpty() && matchesKeywords(textLower, stopWords, "any")) {
            return "stop_words";
        }

        boolean requireAttachments = bool(filters.get("require_attachments"), false);
        if (requireAttachments && attachments.isEmpty()) {
            return "require_attachments";
        }
        Set<String> allowed = stringList(filters.get("attachment_types")).stream()
                .map(AttachmentTypes::canonicalize)
                .collect(Collectors.toSet());
        if (!allowed.isEmpty() && !attachments.isEmpty()) {
            boolean anyAllowed = AttachmentTypes.containsAny(presentTypes, allowed);
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
                    .anyMatch(k -> text.matches("(?su).*\\b" + java.util.regex.Pattern.quote(k) + "\\b.*"));
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

    private static int intVal(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return defaultValue;
    }

    private static Integer intOrNull(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value != null && !value.toString().isBlank()) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static double doubleVal(Object value, double defaultValue) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return defaultValue;
    }
}
