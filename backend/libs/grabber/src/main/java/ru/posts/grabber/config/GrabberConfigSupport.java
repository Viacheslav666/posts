package ru.posts.grabber.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Defaults and accessors for automation_rules.config jsonb (grabber.md §5.1). */
public final class GrabberConfigSupport {

    public static final String PUBLISH_MODE_MODERATE = "moderate";
    public static final String PUBLISH_MODE_AUTO = "auto";
    public static final String PUBLISH_MODE_DRAFT = "draft";

    public static final Set<Integer> ALLOWED_SCAN_INTERVALS = Set.of(15, 30, 60, 120, 180, 360, 720, 1440);

    private GrabberConfigSupport() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> withDefaults(Map<String, Object> input) {
        Map<String, Object> config = new LinkedHashMap<>();
        if (input != null) {
            config.putAll(input);
        }
        config.putIfAbsent("source_ids", List.of());
        config.putIfAbsent("scan_interval_minutes", 180);
        config.putIfAbsent("publish_mode", PUBLISH_MODE_MODERATE);
        config.putIfAbsent("schedule_mode", "immediate");
        config.putIfAbsent("publish_delay_minutes", 0);

        Map<String, Object> filters = map(config.get("filters"));
        filters.putIfAbsent("keyword_include", List.of());
        filters.putIfAbsent("keyword_exclude", List.of());
        filters.putIfAbsent("keyword_match", "any");
        filters.putIfAbsent("attachment_types", List.of("photo", "video", "doc", "link", "audio", "poll", "none"));
        filters.putIfAbsent("require_attachments", false);
        filters.putIfAbsent("exclude_ads", true);
        filters.putIfAbsent("exclude_reposts", true);
        config.put("filters", filters);

        Map<String, Object> transform = map(config.get("transform"));
        transform.putIfAbsent("prepend_text", "");
        transform.putIfAbsent("append_text", "");
        transform.putIfAbsent("strip_hashtags", false);
        transform.putIfAbsent("strip_mentions", false);
        transform.putIfAbsent("text_max_length", null);
        config.put("transform", transform);

        Map<String, Object> attribution = map(config.get("attribution"));
        attribution.putIfAbsent("enabled", true);
        attribution.putIfAbsent("placement", "footer");
        attribution.putIfAbsent("template", "Источник: {source_name} — {source_url}");
        config.put("attribution", attribution);

        Map<String, Object> limits = map(config.get("limits"));
        limits.putIfAbsent("rate_limit_per_hour", 10);
        limits.putIfAbsent("rate_limit_per_day", 50);
        config.put("limits", limits);

        Map<String, Object> reliability = map(config.get("reliability"));
        reliability.putIfAbsent("dedup_enabled", true);
        reliability.putIfAbsent("dedup_scope", "channel");
        reliability.putIfAbsent("ignore_edits", true);
        reliability.putIfAbsent("auto_pause_on_errors", true);
        reliability.putIfAbsent("auto_pause_error_threshold", 5);
        config.put("reliability", reliability);

        config.putIfAbsent("ai_rewrite_enabled", false);
        config.putIfAbsent("rss_max_items_per_run", 20);
        return config;
    }

    public static String publishMode(Map<String, Object> config) {
        Object mode = config == null ? null : config.get("publish_mode");
        if (mode == null || mode.toString().isBlank()) {
            return PUBLISH_MODE_MODERATE;
        }
        return mode.toString().toLowerCase(Locale.ROOT);
    }

    public static int scanIntervalMinutes(Map<String, Object> config) {
        Object value = config == null ? null : config.get("scan_interval_minutes");
        int minutes = value instanceof Number n ? n.intValue() : 180;
        if (!ALLOWED_SCAN_INTERVALS.contains(minutes)) {
            throw new IllegalArgumentException("scan_interval_minutes must be one of " + ALLOWED_SCAN_INTERVALS);
        }
        return minutes;
    }

    public static boolean attributionEnabled(Map<String, Object> config) {
        Map<String, Object> attribution = map(config == null ? null : config.get("attribution"));
        Object enabled = attribution.get("enabled");
        return enabled == null || Boolean.TRUE.equals(enabled) || "true".equalsIgnoreCase(String.valueOf(enabled));
    }

    public static String attributionTemplate(Map<String, Object> config) {
        Map<String, Object> attribution = map(config == null ? null : config.get("attribution"));
        Object template = attribution.get("template");
        if (template == null || template.toString().isBlank()) {
            return "Источник: {source_name} — {source_url}";
        }
        return template.toString();
    }

    @SuppressWarnings("unchecked")
    public static List<String> sourceIds(Map<String, Object> config) {
        Object raw = config == null ? null : config.get("source_ids");
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    out.add(item.toString());
                }
            }
            return out;
        }
        return List.of();
    }

    public static String rssUrl(Map<String, Object> config) {
        Object raw = config == null ? null : config.get("rss_url");
        return raw == null ? null : raw.toString();
    }

    public static boolean dedupEnabled(Map<String, Object> config) {
        Map<String, Object> reliability = map(config == null ? null : config.get("reliability"));
        Object enabled = reliability.get("dedup_enabled");
        return enabled == null || Boolean.TRUE.equals(enabled) || "true".equalsIgnoreCase(String.valueOf(enabled));
    }

    public static int autoPauseThreshold(Map<String, Object> config) {
        Map<String, Object> reliability = map(config == null ? null : config.get("reliability"));
        Object value = reliability.get("auto_pause_error_threshold");
        return value instanceof Number n ? n.intValue() : 5;
    }

    public static boolean autoPauseOnErrors(Map<String, Object> config) {
        Map<String, Object> reliability = map(config == null ? null : config.get("reliability"));
        Object enabled = reliability.get("auto_pause_on_errors");
        return enabled == null || Boolean.TRUE.equals(enabled) || "true".equalsIgnoreCase(String.valueOf(enabled));
    }

    public static int rateLimitPerHour(Map<String, Object> config) {
        Map<String, Object> limits = map(config == null ? null : config.get("limits"));
        Object value = limits.get("rate_limit_per_hour");
        return value instanceof Number n ? n.intValue() : 10;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> filters(Map<String, Object> config) {
        return map(config == null ? null : config.get("filters"));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> transform(Map<String, Object> config) {
        return map(config == null ? null : config.get("transform"));
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
