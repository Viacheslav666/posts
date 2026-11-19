package ru.posts.suggest.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Defaults and accessors for suggest_settings.config jsonb (BE-34). */
public final class SuggestConfigSupport {

    public static final int CURRENT_CONFIG_VERSION = 1;

    public static final String REJECT_DELETE = "delete";
    public static final String REJECT_KEEP = "keep";
    public static final String REJECT_DELETE_AFTER = "delete_after";

    public static final String KEYWORD_CONTAINS = "contains";
    public static final String KEYWORD_WHOLE_WORD = "whole_word";

    public static final String PUBLISH_EDIT_WALL_POST = "wall_post_edit";
    public static final String PUBLISH_EDIT_FALLBACK = "fallback_new_post";

    private static final Set<String> REJECT_POLICIES = Set.of(REJECT_DELETE, REJECT_KEEP, REJECT_DELETE_AFTER);
    private static final Set<String> KEYWORD_MATCHES = Set.of(KEYWORD_CONTAINS, KEYWORD_WHOLE_WORD);
    private static final Set<String> PUBLISH_EDIT_MODES = Set.of(PUBLISH_EDIT_WALL_POST, PUBLISH_EDIT_FALLBACK);

    private SuggestConfigSupport() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> withDefaults(Map<String, Object> input) {
        Map<String, Object> config = new LinkedHashMap<>();
        if (input != null) {
            config.putAll(input);
        }

        Map<String, Object> filters = map(config.get("filters"));
        filters.putIfAbsent("keyword_include", List.of());
        filters.putIfAbsent("keyword_exclude", List.of());
        filters.putIfAbsent("stop_words", List.of());
        filters.putIfAbsent("keyword_match", KEYWORD_CONTAINS);
        filters.putIfAbsent("require_text", false);
        filters.putIfAbsent("require_image", false);
        filters.putIfAbsent("forbid_attachments", List.of());
        filters.putIfAbsent("require_messages_allowed", false);
        filters.putIfAbsent("forbid_closed_profile", false);
        filters.putIfAbsent("author_blacklist", List.of());
        filters.putIfAbsent("author_whitelist", List.of());
        config.put("filters", filters);

        Map<String, Object> limits = map(config.get("limits"));
        Map<String, Object> perAuthor = map(limits.get("per_author"));
        perAuthor.putIfAbsent("hour", 0);
        perAuthor.putIfAbsent("day", 0);
        perAuthor.putIfAbsent("week", 0);
        limits.put("per_author", perAuthor);
        Map<String, Object> global = map(limits.get("global"));
        global.putIfAbsent("hour", 0);
        global.putIfAbsent("day", 0);
        global.putIfAbsent("week", 0);
        limits.put("global", global);
        config.put("limits", limits);

        Map<String, Object> schedule = map(config.get("schedule"));
        schedule.putIfAbsent("enabled", false);
        schedule.putIfAbsent("timezone", "Europe/Moscow");
        schedule.putIfAbsent(
                "windows",
                List.of(Map.of(
                        "days_of_week", List.of(1, 2, 3, 4, 5),
                        "from", "09:00",
                        "to", "21:00")));
        schedule.putIfAbsent("min_interval_minutes", 0);
        schedule.putIfAbsent("pause_after_ads_minutes", 0);
        config.put("schedule", schedule);

        Map<String, Object> dedup = map(config.get("dedup"));
        dedup.putIfAbsent("enabled", true);
        dedup.putIfAbsent("lookback_days", 7);
        dedup.putIfAbsent("similarity_threshold", 0.80d);
        config.put("dedup", dedup);

        config.putIfAbsent("reject_policy", REJECT_DELETE);
        config.putIfAbsent("delete_after_hours", 24);
        config.putIfAbsent("notify_author", true);

        Map<String, Object> firstComment = map(config.get("first_comment"));
        firstComment.putIfAbsent("enabled", false);
        firstComment.putIfAbsent("text", "");
        firstComment.putIfAbsent("attachments", List.of());
        config.put("first_comment", firstComment);

        config.putIfAbsent("signed", true);
        config.putIfAbsent("publish_edit_mode", PUBLISH_EDIT_WALL_POST);

        Map<String, Object> enrichment = map(config.get("enrichment"));
        enrichment.putIfAbsent("hashtags", List.of());
        enrichment.putIfAbsent("append_text", "");
        enrichment.putIfAbsent("media_asset_ids", List.of());
        enrichment.putIfAbsent("keyword_signatures", List.of());
        config.put("enrichment", enrichment);

        Map<String, Object> adsPause = map(config.get("ads_pause"));
        adsPause.putIfAbsent("enabled", true);
        adsPause.putIfAbsent("detect_marked_as_ads", true);
        adsPause.putIfAbsent("detect_erid", true);
        adsPause.putIfAbsent("pause_after_ads_minutes", 60);
        config.put("ads_pause", adsPause);

        config.put("config_version", CURRENT_CONFIG_VERSION);
        return config;
    }

    public static void validate(Map<String, Object> config) {
        Map<String, Object> normalized = withDefaults(config);
        String reject = string(normalized.get("reject_policy"), REJECT_DELETE);
        if (!REJECT_POLICIES.contains(reject)) {
            throw new IllegalArgumentException("Invalid reject_policy: " + reject);
        }
        String match = string(filters(normalized).get("keyword_match"), KEYWORD_CONTAINS);
        if (!KEYWORD_MATCHES.contains(match)) {
            throw new IllegalArgumentException("Invalid keyword_match: " + match);
        }
        String editMode = string(normalized.get("publish_edit_mode"), PUBLISH_EDIT_WALL_POST);
        if (!PUBLISH_EDIT_MODES.contains(editMode)) {
            throw new IllegalArgumentException("Invalid publish_edit_mode: " + editMode);
        }
        int deleteAfter = intVal(normalized.get("delete_after_hours"), 24);
        if (deleteAfter < 0) {
            throw new IllegalArgumentException("delete_after_hours must be >= 0");
        }
        Map<String, Object> schedule = schedule(normalized);
        string(schedule.get("timezone"), "Europe/Moscow");
        Object windows = schedule.get("windows");
        if (windows != null && !(windows instanceof List<?>)) {
            throw new IllegalArgumentException("schedule.windows must be an array");
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> filters(Map<String, Object> config) {
        return map(withDefaults(config).get("filters"));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> limits(Map<String, Object> config) {
        return map(withDefaults(config).get("limits"));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> schedule(Map<String, Object> config) {
        return map(withDefaults(config).get("schedule"));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> dedup(Map<String, Object> config) {
        return map(withDefaults(config).get("dedup"));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> firstComment(Map<String, Object> config) {
        return map(withDefaults(config).get("first_comment"));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> enrichment(Map<String, Object> config) {
        return map(withDefaults(config).get("enrichment"));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> adsPause(Map<String, Object> config) {
        return map(withDefaults(config).get("ads_pause"));
    }

    public static boolean signed(Map<String, Object> config) {
        return bool(withDefaults(config).get("signed"), true);
    }

    public static boolean notifyAuthor(Map<String, Object> config) {
        return bool(withDefaults(config).get("notify_author"), true);
    }

    public static String rejectPolicy(Map<String, Object> config) {
        return string(withDefaults(config).get("reject_policy"), REJECT_DELETE);
    }

    public static int deleteAfterHours(Map<String, Object> config) {
        return intVal(withDefaults(config).get("delete_after_hours"), 24);
    }

    public static String publishEditMode(Map<String, Object> config) {
        return string(withDefaults(config).get("publish_edit_mode"), PUBLISH_EDIT_WALL_POST);
    }

    public static String keywordMatch(Map<String, Object> config) {
        return string(filters(config).get("keyword_match"), KEYWORD_CONTAINS);
    }

    public static double similarityThreshold(Map<String, Object> config) {
        return doubleVal(dedup(config).get("similarity_threshold"), 0.80d);
    }

    public static int lookbackDays(Map<String, Object> config) {
        return intVal(dedup(config).get("lookback_days"), 7);
    }

    public static boolean dedupEnabled(Map<String, Object> config) {
        return bool(dedup(config).get("enabled"), true);
    }

    public static List<String> stringList(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    out.add(item.toString());
                }
            }
            return out;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object raw) {
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return new LinkedHashMap<>();
    }

    public static boolean bool(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }

    public static int intVal(Object value, int defaultValue) {
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

    public static double doubleVal(Object value, double defaultValue) {
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

    public static String string(Object raw, String defaultValue) {
        if (raw == null || raw.toString().isBlank()) {
            return defaultValue;
        }
        return raw.toString();
    }

    /** Normalize text for keyword matching: lowercase + ё→е. */
    public static String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.replace('ё', 'е').replace('Ё', 'е');
    }
}
