package ru.posts.crosspost.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Defaults + typed accessors for crosspost_rules.config jsonb. */
public final class CrosspostConfigSupport {

    private CrosspostConfigSupport() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> withDefaults(Map<String, Object> raw) {
        Map<String, Object> config = raw == null ? new LinkedHashMap<>() : new LinkedHashMap<>(raw);
        Map<String, Object> filters = map(config.get("filters"));
        filters.putIfAbsent("exclude_links", false);
        filters.putIfAbsent("exclude_snippets", true);
        filters.putIfAbsent("exclude_articles", true);
        filters.putIfAbsent("skip_if_no_text", false);
        filters.putIfAbsent("skip_if_no_images", false);
        filters.putIfAbsent("exclude_video", false);
        filters.putIfAbsent("exclude_clips", true);
        filters.putIfAbsent("exclude_poll", true);
        filters.putIfAbsent("exclude_geo", true);
        filters.putIfAbsent("exclude_audio", true);
        filters.putIfAbsent("exclude_ads", true);
        filters.putIfAbsent("exclude_reposts", false);
        filters.putIfAbsent("include_reposts", true);
        filters.putIfAbsent("only_group_posts_in_tg_groups", true);
        config.put("filters", filters);

        Map<String, Object> transform = map(config.get("transform"));
        transform.putIfAbsent("remove_links", false);
        transform.putIfAbsent("remove_hashtags", false);
        transform.putIfAbsent("add_source_link", false);
        transform.putIfAbsent("preserve_formatting", true);
        transform.putIfAbsent("close_comments", false);
        transform.putIfAbsent("publish_as_vk_repost", false);
        transform.putIfAbsent("long_text_mode", "split"); // split | truncate_read_more
        transform.putIfAbsent("vk_video_as_link", true);
        config.put("transform", transform);

        config.putIfAbsent("delay_minutes", 0);
        config.putIfAbsent("include_posts_published_via_posts", false);
        config.putIfAbsent("include_suggest_published", false);
        config.putIfAbsent("circuit_max_items", 60);
        config.putIfAbsent("circuit_max_loop_skips", 5);

        Map<String, Object> schedule = map(config.get("schedule"));
        schedule.putIfAbsent("enabled", false);
        schedule.putIfAbsent("timezone", "Europe/Moscow");
        schedule.putIfAbsent("days_of_week", List.of(1, 2, 3, 4, 5, 6, 7));
        schedule.putIfAbsent("hours_from", 0);
        schedule.putIfAbsent("hours_to", 24);
        config.put("schedule", schedule);
        return config;
    }

    public static Map<String, Object> filters(Map<String, Object> config) {
        return map(withDefaults(config).get("filters"));
    }

    public static Map<String, Object> transform(Map<String, Object> config) {
        return map(withDefaults(config).get("transform"));
    }

    public static int delayMinutes(Map<String, Object> config) {
        Object raw = withDefaults(config).get("delay_minutes");
        if (raw instanceof Number n) {
            return Math.max(0, n.intValue());
        }
        return 0;
    }

    public static boolean includePostsPublishedViaPosts(Map<String, Object> config) {
        return bool(withDefaults(config).get("include_posts_published_via_posts"), false);
    }

    public static boolean includeSuggestPublished(Map<String, Object> config) {
        return bool(withDefaults(config).get("include_suggest_published"), false);
    }

    public static int circuitMaxItems(Map<String, Object> config, int fallback) {
        Object raw = withDefaults(config).get("circuit_max_items");
        if (raw instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        return fallback;
    }

    public static int circuitMaxLoopSkips(Map<String, Object> config, int fallback) {
        Object raw = withDefaults(config).get("circuit_max_loop_skips");
        if (raw instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        return fallback;
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

    public static boolean bool(Object raw, boolean def) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    public static List<String> stringList(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(o.toString());
                }
            }
            return out;
        }
        return List.of();
    }
}
