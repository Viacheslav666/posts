package ru.posts.grabber.target;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single capability matrix for grabber target modes.
 *
 * <p>Other features (action button, first comment, comment attachments, scheduled delete) must call
 * {@link #supports(String, int, Feature)} instead of hard-coding postponed/interval rules.
 *
 * <p>Postponed VK modes ({@code vk_postponed_post}, {@code vk_postponed_repost}) disable the action
 * button, first comment, comment attachments, scheduled delete, and the interval between posts.
 * Comment attachments are also disabled when {@code limits.min_interval_minutes} is greater than 0.
 *
 * <p>Config keys other features should use (enforced on save when present):
 *
 * <ul>
 *   <li>{@code action_button} or {@code transform.action_button} — map with {@code enabled=true} or
 *       any non-empty payload
 *   <li>{@code first_comment.enabled} and {@code first_comment.attachments}
 *   <li>{@code scheduled_delete} ({@code enabled} or {@code minutes}) or {@code delete_after_minutes}
 *       (also under {@code transform})
 *   <li>{@code limits.min_interval_minutes}
 * </ul>
 */
public final class GrabberModeMatrix {

    public static final int POSTPONED_HORIZON_DAYS = 7;
    public static final int MAX_INTERVAL_MINUTES = 120;
    public static final int MAX_SOURCES = 20;
    public static final int DEFAULT_MAX_AGE_DAYS = 60;

    /** Shown when {@code vk_postponed_repost} is listed but not selectable. */
    public static final String POSTPONED_REPOST_UNAVAILABLE_HINT = "Скоро: требует проверки на VK";

    /** Features turned off when the interval between posts is greater than zero. */
    public static final List<String> INTERVAL_DISABLES = List.of(Feature.COMMENT_ATTACHMENTS.wire());

    private GrabberModeMatrix() {}

    public enum Feature {
        ACTION_BUTTON("actionButton"),
        FIRST_COMMENT("firstComment"),
        COMMENT_ATTACHMENTS("commentAttachments"),
        SCHEDULED_DELETE("scheduledDelete"),
        INTERVAL("interval");

        private final String wire;

        Feature(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }
    }

    public static boolean supports(String targetMode, int intervalMinutes, Feature feature) {
        GrabberTargetMode mode = GrabberTargetMode.fromWire(targetMode);
        if (mode.postponed()) {
            return false;
        }
        if (feature == Feature.COMMENT_ATTACHMENTS && intervalMinutes > 0) {
            return false;
        }
        return true;
    }

    public static boolean supports(GrabberTargetMode mode, int intervalMinutes, Feature feature) {
        return supports(mode.wire(), intervalMinutes, feature);
    }

    /**
     * {@code vk_postponed_repost} stays unavailable until {@code posts.grabber.postponed-repost.enabled}
     * is turned on. {@code wall.repost} has no documented {@code publish_date}.
     */
    public static boolean available(GrabberTargetMode mode, boolean postponedRepostEnabled) {
        if (mode == GrabberTargetMode.VK_POSTPONED_REPOST) {
            return postponedRepostEnabled;
        }
        return true;
    }

    /** Full matrix for the UI. Postponed repost is unavailable unless the flag is on. */
    public static Map<String, Object> describe() {
        return describe(false);
    }

    public static Map<String, Object> describe(boolean postponedRepostEnabled) {
        List<Map<String, Object>> modes = new ArrayList<>();
        for (GrabberTargetMode mode : GrabberTargetMode.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", mode.wire());
            row.put("postponed", mode.postponed());
            row.put("repost", mode.repost());
            row.put("available", available(mode, postponedRepostEnabled));
            row.put(Feature.ACTION_BUTTON.wire(), supports(mode, 0, Feature.ACTION_BUTTON));
            row.put(Feature.FIRST_COMMENT.wire(), supports(mode, 0, Feature.FIRST_COMMENT));
            row.put(Feature.COMMENT_ATTACHMENTS.wire(), supports(mode, 0, Feature.COMMENT_ATTACHMENTS));
            row.put(Feature.SCHEDULED_DELETE.wire(), supports(mode, 0, Feature.SCHEDULED_DELETE));
            row.put(Feature.INTERVAL.wire(), supports(mode, 0, Feature.INTERVAL));
            modes.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("modes", modes);
        body.put("intervalDisables", INTERVAL_DISABLES);
        body.put("postponedHorizonDays", POSTPONED_HORIZON_DAYS);
        body.put("maxIntervalMinutes", MAX_INTERVAL_MINUTES);
        body.put("maxSources", MAX_SOURCES);
        body.put("defaultMaxAgeDays", DEFAULT_MAX_AGE_DAYS);
        return body;
    }
}
