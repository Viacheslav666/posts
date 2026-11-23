package ru.posts.grabber.target;

import java.util.Locale;
import java.util.Set;

/**
 * Where a grabber rule places copied VK content.
 *
 * <ul>
 *   <li>{@code wall_now} — сразу на стену (пост)
 *   <li>{@code vk_postponed_post} — в отложенные записи VK (пост), {@code publish_date} ≤ 7 дней
 *   <li>{@code wall_repost} — сразу на стену (репост), {@code wall.repost}
 *   <li>{@code vk_postponed_repost} — в отложенные записи VK (репост)
 * </ul>
 */
public enum GrabberTargetMode {
    WALL_NOW("wall_now", false, false),
    VK_POSTPONED_POST("vk_postponed_post", true, false),
    WALL_REPOST("wall_repost", false, true),
    VK_POSTPONED_REPOST("vk_postponed_repost", true, true);

    public static final String DEFAULT = WALL_NOW.wire;

    private static final Set<String> WIRES =
            Set.of(WALL_NOW.wire, VK_POSTPONED_POST.wire, WALL_REPOST.wire, VK_POSTPONED_REPOST.wire);

    private final String wire;
    private final boolean postponed;
    private final boolean repost;

    GrabberTargetMode(String wire, boolean postponed, boolean repost) {
        this.wire = wire;
        this.postponed = postponed;
        this.repost = repost;
    }

    public String wire() {
        return wire;
    }

    public boolean postponed() {
        return postponed;
    }

    public boolean repost() {
        return repost;
    }

    public static boolean isKnown(String raw) {
        return raw != null && WIRES.contains(raw.trim().toLowerCase(Locale.ROOT));
    }

    public static GrabberTargetMode fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return WALL_NOW;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (GrabberTargetMode mode : values()) {
            if (mode.wire.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown grabber target_mode: " + raw);
    }

    public static boolean isPostponed(String raw) {
        return isKnown(raw) && fromWire(raw).postponed;
    }

    public static boolean isRepost(String raw) {
        return isKnown(raw) && fromWire(raw).repost;
    }
}
