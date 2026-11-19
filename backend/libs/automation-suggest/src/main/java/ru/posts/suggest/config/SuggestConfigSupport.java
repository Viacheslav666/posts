package ru.posts.suggest.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Defaults and accessors for suggest_settings.config jsonb (BE-34). */
public final class SuggestConfigSupport {

    public static final int CURRENT_CONFIG_VERSION = 1;

    public static final String KEYWORD_MATCH_SUBSTRING = "substring";
    public static final String KEYWORD_MATCH_WHOLE_WORD = "whole_word";

    public static final String ON_REJECT_DELETE = "delete";
    public static final String ON_REJECT_KEEP = "keep";
    public static final String ON_REJECT_DELETE_AFTER = "delete_after";

    public static final String DEFAULT_TIMEZONE = "Europe/Moscow";

    private SuggestConfigSupport() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> withDefaults(Map<String, Object> input) {
        Map<String, Object> config = new LinkedHashMap<>();
        if (input != null) {
            config.putAll(input);
        }

        Map<String, Object> filters = map(config.get("filters"));
        filters.putIfAbsent("author_whitelist", List.of());
        filters.putIfAbsent("author_blacklist", List.of());
        filters.putIfAbsent("reject_closed_profile", false);
        filters.putIfAbsent("require_messages_allowed", false);
        filters.putIfAbsent("skip_if_no_text", false);
        filters.putIfAbsent("skip_if_no_image", false);
        filters.putIfAbsent("skip_if_has_attachment_types", List.of());
        filters.putIfAbsent("exclude_links", false);
        filters.putIfAbsent("keyword_include", List.of());
        filters.putIfAbsent("keyword_exclude", List.of());
        filters.putIfAbsent("stop_words", List.of());
        filters.putIfAbsent("keyword_match_mode", KEYWORD_MATCH_SUBSTRING);
        config.put("filters", filters);

        Map<String, Object> limits = map(config.get("limits"));
        limits.putIfAbsent("channel_per_hour", 0);
        limits.putIfAbsent("channel_per_day", 0);
        limits.putIfAbsent("channel_per_week", 0);
        limits.putIfAbsent("author_per_hour", 0);
        limits.putIfAbsent("author_per_day", 0);
        limits.putIfAbsent("author_per_week", 0);
        limits.putIfAbsent("min_interval_minutes", 0);
        config.put("limits", limits);

        Map<String, Object> schedule = map(config.get("schedule"));
        schedule.putIfAbsent("enabled", false);
        schedule.putIfAbsent("timezone", DEFAULT_TIMEZONE);
        schedule.putIfAbsent("windows", List.of());
        config.put("schedule", schedule);

        Map<String, Object> dedup = map(config.get("dedup"));
        dedup.putIfAbsent("enabled", true);
        dedup.putIfAbsent("lookback_days", 7);
        dedup.putIfAbsent("threshold", 0.8d);
        config.put("dedup", dedup);

        Map<String, Object> publish = map(config.get("publish"));
        publish.putIfAbsent("signed", true);
        publish.putIfAbsent("from_group", true);
        publish.putIfAbsent("pause_after_ads", true);
        publish.putIfAbsent("pause_after_ads_minutes", 60);
        config.put("publish", publish);

        Map<String, Object> onReject = map(config.get("on_reject"));
        onReject.putIfAbsent("policy", ON_REJECT_DELETE);
        onReject.putIfAbsent("delete_after_hours", 24);
        onReject.putIfAbsent("notify_author", false);
        config.put("on_reject", onReject);

        Map<String, Object> firstComment = map(config.get("first_comment"));
        firstComment.putIfAbsent("enabled", false);
        firstComment.putIfAbsent("text", "");
        firstComment.putIfAbsent("attachments", List.of());
        config.put("first_comment", firstComment);

        config.put("config_version", CURRENT_CONFIG_VERSION);
        return config;
    }

    public static int configVersion(Map<String, Object> config) {
        Object value = config == null ? null : config.get("config_version");
        return value instanceof Number n ? n.intValue() : CURRENT_CONFIG_VERSION;
    }

    public static Map<String, Object> filters(Map<String, Object> config) {
        return map(config == null ? null : config.get("filters"));
    }

    public static Map<String, Object> limits(Map<String, Object> config) {
        return map(config == null ? null : config.get("limits"));
    }

    public static Map<String, Object> schedule(Map<String, Object> config) {
        return map(config == null ? null : config.get("schedule"));
    }

    public static Map<String, Object> dedup(Map<String, Object> config) {
        return map(config == null ? null : config.get("dedup"));
    }

    public static Map<String, Object> publish(Map<String, Object> config) {
        return map(config == null ? null : config.get("publish"));
    }

    public static Map<String, Object> onReject(Map<String, Object> config) {
        return map(config == null ? null : config.get("on_reject"));
    }

    public static String keywordMatchMode(Map<String, Object> config) {
        Map<String, Object> filters = filters(config);
        Object mode = filters.get("keyword_match_mode");
        if (mode == null || mode.toString().isBlank()) {
            return KEYWORD_MATCH_SUBSTRING;
        }
        String value = mode.toString().trim().toLowerCase(Locale.ROOT);
        if (KEYWORD_MATCH_WHOLE_WORD.equals(value) || "whole-word".equals(value) || "word".equals(value)) {
            return KEYWORD_MATCH_WHOLE_WORD;
        }
        return KEYWORD_MATCH_SUBSTRING;
    }

    public static String onRejectPolicy(Map<String, Object> config) {
        Map<String, Object> onReject = onReject(config);
        Object policy = onReject.get("policy");
        if (policy == null || policy.toString().isBlank()) {
            return ON_REJECT_DELETE;
        }
        return policy.toString().trim().toLowerCase(Locale.ROOT);
    }

    public static boolean signed(Map<String, Object> config) {
        Map<String, Object> publish = publish(config);
        return bool(publish.get("signed"), true);
    }

    public static boolean dedupEnabled(Map<String, Object> config) {
        Map<String, Object> dedup = dedup(config);
        return bool(dedup.get("enabled"), true);
    }

    public static int dedupLookbackDays(Map<String, Object> config) {
        Map<String, Object> dedup = dedup(config);
        return Math.max(1, intVal(dedup.get("lookback_days"), 7));
    }

    public static double dedupThreshold(Map<String, Object> config) {
        Map<String, Object> dedup = dedup(config);
        return doubleVal(dedup.get("threshold"), 0.8d);
    }

    public static String scheduleTimezone(Map<String, Object> config) {
        Map<String, Object> schedule = schedule(config);
        Object tz = schedule.get("timezone");
        if (tz == null || tz.toString().isBlank()) {
            return DEFAULT_TIMEZONE;
        }
        return tz.toString();
    }

    public static boolean scheduleEnabled(Map<String, Object> config) {
        Map<String, Object> schedule = schedule(config);
        return bool(schedule.get("enabled"), false);
    }

    @SuppressWarnings("unchecked")
    public static List<String> stringList(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    out.add(item.toString());
                }
            }
            return out;
        }
        return List.of();
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
        if (value != null && !value.toString().isBlank()) {
            try {
                return Integer.parseInt(value.toString().trim());
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
        if (value != null && !value.toString().isBlank()) {
            try {
                return Double.parseDouble(value.toString().trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            return copy;
        }
        return new LinkedHashMap<>();
    }
}
