package ru.posts.grabber.publish;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import ru.posts.grabber.config.GrabberConfigSupport;

/**
 * Local publish-mode restrictions for grabber post options.
 *
 * <p>A parallel change is expected to add a central capability matrix for publish modes. That
 * matrix is not on main, so this gate is the fallback. It reads, in order: {@code posting_mode},
 * {@code delivery_mode}, {@code publish_timing}, {@code schedule.posting_mode}, {@code
 * schedule.delivery_mode}, then {@code schedule_mode}.
 *
 * <p>Postponed modes ({@code postponed}, {@code native_deferred}, {@code vk_postponed}, {@code
 * deferred}, {@code vk_delayed}) disable the first comment, comment attachments, the action button
 * and scheduled deletion — those actions cannot be applied to a post that the network has not
 * published yet. Comment attachments are also disabled in interval modes ({@code interval}, {@code
 * timer}, {@code by_interval}). Scheduler modes {@code immediate}, {@code delay} and {@code slot}
 * still allow the options, because Posts publishes them itself and runs the follow-up actions
 * after a successful publish.
 */
public final class GrabberModeGate {

    public static final String MATRIX_NOTE =
            "Local grabber mode gate; replace with the central publish-mode matrix when it lands.";

    private static final Set<String> POSTPONED =
            Set.of("postponed", "native_deferred", "vk_postponed", "deferred", "vk_delayed", "native");

    private static final Set<String> INTERVAL = Set.of("interval", "timer", "by_interval");

    private GrabberModeGate() {}

    public enum Feature {
        FIRST_COMMENT,
        COMMENT_ATTACHMENTS,
        ACTION_BUTTON,
        SCHEDULED_DELETE
    }

    public static boolean allows(Feature feature, Map<String, Object> config) {
        String mode = postingMode(config);
        boolean postponed = POSTPONED.contains(mode);
        boolean interval = INTERVAL.contains(mode);
        return switch (feature) {
            case FIRST_COMMENT, ACTION_BUTTON, SCHEDULED_DELETE -> !postponed;
            case COMMENT_ATTACHMENTS -> !postponed && !interval;
        };
    }

    public static String postingMode(Map<String, Object> config) {
        if (config == null) {
            return "immediate";
        }
        for (String key : new String[] {"posting_mode", "delivery_mode", "publish_timing"}) {
            String value = text(config.get(key));
            if (value != null) {
                return value;
            }
        }
        Map<String, Object> schedule = GrabberConfigSupport.schedule(config);
        for (String key : new String[] {"posting_mode", "delivery_mode", "publish_timing"}) {
            String value = text(schedule.get(key));
            if (value != null) {
                return value;
            }
        }
        String scheduleMode = text(config.get("schedule_mode"));
        return scheduleMode == null ? "immediate" : scheduleMode;
    }

    public static boolean postponed(Map<String, Object> config) {
        return POSTPONED.contains(postingMode(config));
    }

    public static boolean interval(Map<String, Object> config) {
        return INTERVAL.contains(postingMode(config));
    }

    private static String text(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }
}
