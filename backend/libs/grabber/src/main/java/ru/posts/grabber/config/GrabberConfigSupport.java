package ru.posts.grabber.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import ru.posts.grabber.target.GrabberModeMatrix;
import ru.posts.grabber.target.GrabberTargetMode;

/** Defaults and accessors for automation_rules.config jsonb (grabber.md §5.1 + BE-27). */
public final class GrabberConfigSupport {

    public static final String PUBLISH_MODE_MODERATE = "moderate";
    public static final String PUBLISH_MODE_AUTO = "auto";
    public static final String PUBLISH_MODE_DRAFT = "draft";

    /** Config schema version. Older configs are migrated on read via {@link #withDefaults}. */
    public static final int CURRENT_CONFIG_VERSION = 3;

    public static final int MAX_SOURCES_PER_RULE = 20;

    public static final Set<Integer> ALLOWED_SCAN_INTERVALS = Set.of(15, 30, 60, 120, 180, 360, 720, 1440);

    private GrabberConfigSupport() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> withDefaults(Map<String, Object> input) {
        Map<String, Object> config = new LinkedHashMap<>();
        if (input != null) {
            config.putAll(input);
        }
        int fromVersion = config.get("config_version") instanceof Number n ? n.intValue() : 1;
        migrate(config, fromVersion);

        config.putIfAbsent("source_ids", List.of());
        config.putIfAbsent("scan_interval_minutes", 180);
        config.putIfAbsent("publish_mode", PUBLISH_MODE_MODERATE);
        config.putIfAbsent("target_mode", GrabberTargetMode.DEFAULT);
        config.putIfAbsent("schedule_mode", "immediate");
        config.putIfAbsent("publish_delay_minutes", 0);

        Map<String, Object> filters = map(config.get("filters"));
        filters.putIfAbsent("keyword_include", List.of());
        filters.putIfAbsent("keyword_exclude", List.of());
        filters.putIfAbsent("stop_words", List.of());
        filters.putIfAbsent("keyword_match", "any");
        filters.putIfAbsent("attachment_types", List.of());
        filters.putIfAbsent("require_attachments", false);
        filters.putIfAbsent("exclude_ads", true);
        filters.putIfAbsent("exclude_reposts", true);
        filters.putIfAbsent("exclude_links", false);
        filters.putIfAbsent("skip_if_no_text", false);
        filters.putIfAbsent("skip_if_no_media", false);
        filters.putIfAbsent("skip_if_any_text", false);
        filters.putIfAbsent("skip_if_homoglyphs", false);
        filters.putIfAbsent("skip_if_caps", false);
        filters.putIfAbsent("caps_threshold", 0.8d);
        filters.putIfAbsent("caps_min_letters", 8);
        filters.putIfAbsent("skip_if_phone", false);
        filters.putIfAbsent("require_phone", false);
        filters.putIfAbsent("require_phone_or_signature", false);
        filters.putIfAbsent("only_group_posts", false);
        filters.putIfAbsent("skip_if_missing_attachment_types", List.of());
        filters.putIfAbsent("skip_if_no_visual_media", false);
        filters.putIfAbsent("skip_if_no_article", false);
        if (!filters.containsKey("max_age_days")) {
            filters.put("max_age_days", GrabberModeMatrix.DEFAULT_MAX_AGE_DAYS);
        }
        config.put("filters", filters);

        Map<String, Object> transform = map(config.get("transform"));
        transform.putIfAbsent("prepend_text", "");
        transform.putIfAbsent("append_text", "");
        transform.putIfAbsent("strip_hashtags", false);
        transform.putIfAbsent("strip_mentions", false);
        transform.putIfAbsent("strip_all_text", false);
        transform.putIfAbsent("strip_phones", false);
        transform.putIfAbsent("strip_all_links", false);
        transform.putIfAbsent("strip_links", false);
        transform.putIfAbsent("strip_photo", false);
        transform.putIfAbsent("strip_video", false);
        transform.putIfAbsent("strip_clip", false);
        transform.putIfAbsent("strip_audio", false);
        transform.putIfAbsent("strip_music", false);
        transform.putIfAbsent("strip_playlist", false);
        transform.putIfAbsent("strip_doc", false);
        transform.putIfAbsent("strip_file", false);
        transform.putIfAbsent("strip_poll", false);
        transform.putIfAbsent("strip_album", false);
        transform.putIfAbsent("strip_market", false);
        transform.putIfAbsent("strip_product", false);
        transform.putIfAbsent("strip_market_album", false);
        transform.putIfAbsent("strip_wiki", false);
        transform.putIfAbsent("strip_article", false);
        transform.putIfAbsent("strip_attachment_types", List.of());
        transform.putIfAbsent("word_replacements", Map.of());
        transform.putIfAbsent("remove_keywords", List.of());
        transform.putIfAbsent("attached_attachments", List.of());
        transform.putIfAbsent("attach_when_no_attachments", false);
        transform.putIfAbsent("attachments_mode", "grid");
        transform.putIfAbsent("user_attachments", "");
        transform.putIfAbsent("reupload_video", false);
        transform.putIfAbsent("link_to_source", false);
        transform.putIfAbsent("link_to_author", false);
        transform.putIfAbsent("append_author_signature", true);
        transform.putIfAbsent("close_comments", false);
        transform.putIfAbsent("delete_after_enabled", false);
        transform.putIfAbsent("delete_after_amount", 24);
        transform.putIfAbsent("delete_after_unit", "hours");
        transform.putIfAbsent("action_button", Map.of());
        transform.putIfAbsent("ai_model", "stub");
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
        limits.putIfAbsent("rate_limit_per_source_per_day", 0); // 0 = disabled
        limits.putIfAbsent("min_interval_minutes", 0);
        config.put("limits", limits);

        Map<String, Object> reliability = map(config.get("reliability"));
        reliability.putIfAbsent("dedup_enabled", true);
        reliability.putIfAbsent("dedup_scope", "channel");
        reliability.putIfAbsent("similarity_dedup_enabled", true);
        reliability.putIfAbsent("similarity_threshold", 0.8d);
        reliability.putIfAbsent("similarity_lookback", 15);
        reliability.putIfAbsent("ignore_edits", true);
        reliability.putIfAbsent("auto_pause_on_errors", true);
        reliability.putIfAbsent("auto_pause_error_threshold", 5);
        reliability.putIfAbsent("notify_on_errors", true);
        reliability.putIfAbsent("notify_on_error", true);
        config.put("reliability", reliability);

        Map<String, Object> schedule = map(config.get("schedule"));
        schedule.putIfAbsent("enabled", false);
        schedule.putIfAbsent("timezone", "Europe/Moscow");
        schedule.putIfAbsent("days_of_week", List.of(1, 2, 3, 4, 5, 6, 7));
        schedule.putIfAbsent("hours_from", 0);
        schedule.putIfAbsent("hours_to", 24);
        config.put("schedule", schedule);

        Map<String, Object> firstComment = map(config.get("first_comment"));
        firstComment.putIfAbsent("enabled", false);
        firstComment.putIfAbsent("text", "");
        firstComment.putIfAbsent("attachments", "");
        // Kept for old rows. Publishing no longer waits on this flag.
        firstComment.putIfAbsent("pending_be23", false);
        config.put("first_comment", firstComment);

        config.putIfAbsent("macros", Map.of());
        config.putIfAbsent("ai_rewrite_enabled", false);
        config.putIfAbsent("rss_max_items_per_run", 20);
        config.putIfAbsent("website_urls", List.of());
        config.putIfAbsent("website_selector", null);
        config.put("config_version", CURRENT_CONFIG_VERSION);
        return config;
    }

    private static void migrate(Map<String, Object> config, int fromVersion) {
        if (fromVersion >= CURRENT_CONFIG_VERSION) {
            return;
        }
        // v1 → v3: schedule / first_comment / similarity / target_mode defaults (applied by putIfAbsent)
        config.put("config_version", CURRENT_CONFIG_VERSION);
    }

    public static String targetMode(Map<String, Object> config) {
        Object mode = config == null ? null : config.get("target_mode");
        if (mode == null || mode.toString().isBlank()) {
            return GrabberTargetMode.DEFAULT;
        }
        return mode.toString().trim().toLowerCase(Locale.ROOT);
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

    @SuppressWarnings("unchecked")
    public static List<String> websiteUrls(Map<String, Object> config) {
        Object raw = config == null ? null : config.get("website_urls");
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    out.add(item.toString().trim());
                }
            }
            return out;
        }
        return List.of();
    }

    public static String websiteSelector(Map<String, Object> config) {
        Object raw = config == null ? null : config.get("website_selector");
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        return value.isEmpty() ? null : value;
    }

    public static boolean dedupEnabled(Map<String, Object> config) {
        Map<String, Object> reliability = map(config == null ? null : config.get("reliability"));
        Object enabled = reliability.get("dedup_enabled");
        return enabled == null || Boolean.TRUE.equals(enabled) || "true".equalsIgnoreCase(String.valueOf(enabled));
    }

    public static boolean similarityDedupEnabled(Map<String, Object> config) {
        Map<String, Object> reliability = map(config == null ? null : config.get("reliability"));
        Object enabled = reliability.get("similarity_dedup_enabled");
        return enabled == null || Boolean.TRUE.equals(enabled) || "true".equalsIgnoreCase(String.valueOf(enabled));
    }

    public static double similarityThreshold(Map<String, Object> config) {
        Map<String, Object> reliability = map(config == null ? null : config.get("reliability"));
        Object value = reliability.get("similarity_threshold");
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return 0.8d;
    }

    public static int similarityLookback(Map<String, Object> config) {
        Map<String, Object> reliability = map(config == null ? null : config.get("reliability"));
        Object value = reliability.get("similarity_lookback");
        int n = value instanceof Number num ? num.intValue() : 15;
        return Math.max(1, Math.min(n, 100));
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

    public static boolean notifyOnErrors(Map<String, Object> config) {
        Map<String, Object> reliability = map(config == null ? null : config.get("reliability"));
        Object enabled = reliability.get("notify_on_errors");
        return enabled == null || Boolean.TRUE.equals(enabled) || "true".equalsIgnoreCase(String.valueOf(enabled));
    }

    /** When false, setting failures are only logged — no {@code grabber.setting_error} notification. */
    public static boolean notifyOnError(Map<String, Object> config) {
        Map<String, Object> reliability = map(config == null ? null : config.get("reliability"));
        Object enabled = reliability.get("notify_on_error");
        if (enabled == null) {
            return true;
        }
        if (enabled instanceof Boolean b) {
            return b;
        }
        return !"false".equalsIgnoreCase(enabled.toString());
    }

    public static boolean reuploadVideo(Map<String, Object> config) {
        Object enabled = transform(config).get("reupload_video");
        return Boolean.TRUE.equals(enabled) || "true".equalsIgnoreCase(String.valueOf(enabled));
    }

    public static String userAttachments(Map<String, Object> config) {
        Object raw = transform(config).get("user_attachments");
        return raw == null ? "" : raw.toString();
    }

    public static boolean carouselMode(Map<String, Object> config) {
        Object mode = transform(config).get("attachments_mode");
        return mode != null && "carousel".equalsIgnoreCase(mode.toString().trim());
    }

    /**
     * {@code 0} (and an explicit JSON null) means the age filter is off. A missing key defaults to
     * {@link GrabberModeMatrix#DEFAULT_MAX_AGE_DAYS}.
     */
    public static int maxAgeDays(Map<String, Object> config) {
        Map<String, Object> filters = map(config == null ? null : config.get("filters"));
        if (!filters.containsKey("max_age_days") || filters.get("max_age_days") == null) {
            return filters.containsKey("max_age_days") ? 0 : GrabberModeMatrix.DEFAULT_MAX_AGE_DAYS;
        }
        Object value = filters.get("max_age_days");
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            return GrabberModeMatrix.DEFAULT_MAX_AGE_DAYS;
        }
    }

    public static int rateLimitPerHour(Map<String, Object> config) {
        Map<String, Object> limits = map(config == null ? null : config.get("limits"));
        Object value = limits.get("rate_limit_per_hour");
        return value instanceof Number n ? n.intValue() : 10;
    }

    public static int rateLimitPerDay(Map<String, Object> config) {
        Map<String, Object> limits = map(config == null ? null : config.get("limits"));
        Object value = limits.get("rate_limit_per_day");
        return value instanceof Number n ? n.intValue() : 50;
    }

    public static int rateLimitPerSourcePerDay(Map<String, Object> config) {
        Map<String, Object> limits = map(config == null ? null : config.get("limits"));
        Object value = limits.get("rate_limit_per_source_per_day");
        return value instanceof Number n ? n.intValue() : 0;
    }

    /** Stored interval before the 0–120 clamp. Validation uses this so a value above 120 is rejected. */
    public static int declaredMinIntervalMinutes(Map<String, Object> config) {
        Map<String, Object> limits = map(config == null ? null : config.get("limits"));
        Object value = limits.get("min_interval_minutes");
        if (value instanceof Number num) {
            return num.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return 0;
    }

    public static int minIntervalMinutes(Map<String, Object> config) {
        int n = declaredMinIntervalMinutes(config);
        return Math.max(0, Math.min(n, 120));
    }

    /** IANA zone from {@code schedule.timezone}, or {@code Europe/Moscow} when missing or invalid. */
    public static String scheduleTimezone(Map<String, Object> config) {
        Object raw = schedule(config).get("timezone");
        String value = raw == null || raw.toString().isBlank()
                ? "Europe/Moscow"
                : raw.toString().trim();
        try {
            return java.time.ZoneId.of(value).getId();
        } catch (java.time.DateTimeException ex) {
            return "Europe/Moscow";
        }
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
    public static Map<String, Object> schedule(Map<String, Object> config) {
        return map(config == null ? null : config.get("schedule"));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> firstComment(Map<String, Object> config) {
        return map(config == null ? null : config.get("first_comment"));
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

    public static boolean aiRewriteEnabled(Map<String, Object> config) {
        Object enabled = config == null ? null : config.get("ai_rewrite_enabled");
        return bool(enabled, false);
    }

    public static String aiModel(Map<String, Object> config) {
        Object value = transform(config).get("ai_model");
        if (value == null || value.toString().isBlank()) {
            return "stub";
        }
        return value.toString().trim();
    }

    public static boolean appendAuthorSignature(Map<String, Object> config) {
        return bool(transform(config).get("append_author_signature"), true);
    }

    /** {@code null} when the timer is off or the amount/unit is not usable. */
    public static java.time.Duration deleteAfter(Map<String, Object> config) {
        Map<String, Object> transform = transform(config);
        if (!bool(transform.get("delete_after_enabled"), false)) {
            return null;
        }
        int amount = intVal(transform.get("delete_after_amount"), 0);
        String unit = transform.get("delete_after_unit") == null
                ? ""
                : transform.get("delete_after_unit").toString().trim().toLowerCase(java.util.Locale.ROOT);
        if (amount <= 0) {
            return null;
        }
        return switch (unit) {
            case "minutes" -> amount > 525_600 ? null : java.time.Duration.ofMinutes(amount);
            case "hours" -> amount > 8_760 ? null : java.time.Duration.ofHours(amount);
            case "days" -> amount > 365 ? null : java.time.Duration.ofDays(amount);
            default -> null;
        };
    }

    /**
     * Rejects settings that cannot be stored. Mode skips are not errors: they are applied at
     * publish time and reported as notifications.
     */
    public static void validateSettings(Map<String, Object> config) {
        stripClientAiPrice(config);
        Map<String, Object> transform = transform(config);
        if (bool(transform.get("delete_after_enabled"), false) && deleteAfter(config) == null) {
            throw new IllegalArgumentException(
                    "Таймер удаления: укажите положительное число и единицу minutes, hours или days "
                            + "(не больше года).");
        }
        Object button = transform.get("action_button");
        if (button instanceof Map<?, ?> map && !map.isEmpty()) {
            Map<String, Object> copy = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null && v != null) {
                    copy.put(String.valueOf(k), v);
                }
            });
            String error = ru.posts.grabber.publish.GrabberPublishOptions.actionButtonError(copy);
            if (error != null) {
                throw new IllegalArgumentException(error);
            }
        }
        Map<String, Object> firstComment = firstComment(config);
        Object attachments = firstComment.get("attachments");
        String attachmentText = attachmentText(attachments);
        java.util.List<String> invalid = ru.posts.grabber.publish.CommentAttachments.invalidTokens(attachmentText);
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException(
                    "Вложения комментария: допустимы photo-ID_ID и video-ID_ID, в том числе внутри [rand]. Неверно: "
                            + String.join(", ", invalid));
        }
        if (aiRewriteEnabled(config)) {
            String model = aiModel(config);
            if (!model.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")) {
                throw new IllegalArgumentException("Модель нейросети: латиница, цифры и . _ : - , до 64 символов.");
            }
        }
        if (bool(firstComment.get("enabled"), false)) {
            String text = firstComment.get("text") == null
                    ? ""
                    : firstComment.get("text").toString().trim();
            if (text.isEmpty() && attachmentText.isBlank()) {
                throw new IllegalArgumentException("Первый комментарий включён, но текст пустой.");
            }
        }
    }

    /** The client cannot set the neural price. The charge is {@code posts.grabber.ai.cost-kopecks}. */
    private static void stripClientAiPrice(Map<String, Object> config) {
        if (config == null) {
            return;
        }
        Object raw = config.get("transform");
        if (!(raw instanceof Map<?, ?> transform) || !transform.containsKey("ai_cost_kopecks")) {
            return;
        }
        try {
            transform.remove("ai_cost_kopecks");
        } catch (UnsupportedOperationException ex) {
            Map<String, Object> copy = new java.util.LinkedHashMap<>();
            transform.forEach((k, v) -> {
                if (k != null && !"ai_cost_kopecks".equals(String.valueOf(k))) {
                    copy.put(String.valueOf(k), v);
                }
            });
            config.put("transform", copy);
        }
    }

    private static String attachmentText(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof java.util.List<?> list) {
            return list.stream()
                    .filter(item -> item != null && !item.toString().isBlank())
                    .map(Object::toString)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
        }
        return raw.toString();
    }

    private static int intVal(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value != null && !value.toString().isBlank()) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
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
