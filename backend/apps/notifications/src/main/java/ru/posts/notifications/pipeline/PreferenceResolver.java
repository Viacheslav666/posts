package ru.posts.notifications.pipeline;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import ru.posts.persistence.entity.NotificationSettingsEntity;

/**
 * Resolves type × channel preferences. Empty/missing settings fall back to BE-11 defaults.
 *
 * <p>preferences jsonb shape:
 *
 * <pre>
 * {
 *   "publication.failed": {"email":true,"telegram":true,"web_push":true,"in_app":true,"ws":true},
 *   "mutedTypes": ["billing.reminder"]
 * }
 * </pre>
 */
public final class PreferenceResolver {

    private static final Map<String, ChannelPreference> DEFAULTS = defaultMatrix();

    private PreferenceResolver() {}

    public static ChannelPreference resolve(NotificationSettingsEntity settings, String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return ChannelPreference.none();
        }
        if (isMuted(settings, eventType)) {
            return ChannelPreference.none();
        }
        ChannelPreference defaults = DEFAULTS.getOrDefault(eventType, allTrue());
        if (settings == null
                || settings.getPreferences() == null
                || settings.getPreferences().isEmpty()) {
            return applyEmailUnsubscribed(settings, defaults);
        }
        Object raw = settings.getPreferences().get(eventType);
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return applyEmailUnsubscribed(settings, defaults);
        }
        ChannelPreference resolved = new ChannelPreference(
                bool(map, NotificationChannels.EMAIL, defaults.email()),
                bool(map, NotificationChannels.TELEGRAM, defaults.telegram()),
                bool(map, NotificationChannels.WEB_PUSH, defaults.webPush()),
                bool(map, NotificationChannels.IN_APP, defaults.inApp()),
                bool(map, NotificationChannels.WS, defaults.ws()));
        return applyEmailUnsubscribed(settings, resolved);
    }

    public static Map<String, ChannelPreference> defaults() {
        return Collections.unmodifiableMap(DEFAULTS);
    }

    static boolean isMuted(NotificationSettingsEntity settings, String eventType) {
        if (settings == null || settings.getPreferences() == null) {
            return false;
        }
        Object muted = settings.getPreferences().get("mutedTypes");
        if (muted == null) {
            muted = settings.getPreferences().get("muted");
        }
        if (muted instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && eventType.equalsIgnoreCase(String.valueOf(item))) {
                    return true;
                }
            }
        }
        if (muted instanceof Set<?> set) {
            for (Object item : set) {
                if (item != null && eventType.equalsIgnoreCase(String.valueOf(item))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ChannelPreference applyEmailUnsubscribed(
            NotificationSettingsEntity settings, ChannelPreference prefs) {
        if (settings != null && settings.isEmailUnsubscribedAll() && prefs.email()) {
            return new ChannelPreference(false, prefs.telegram(), prefs.webPush(), prefs.inApp(), prefs.ws());
        }
        return prefs;
    }

    private static boolean bool(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value == null) {
            // accept camelCase aliases
            value = map.get(
                    switch (key) {
                        case "web_push" -> "webPush";
                        case "in_app" -> "inApp";
                        default -> key;
                    });
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s.toLowerCase(Locale.ROOT));
        }
        return fallback;
    }

    private static ChannelPreference allTrue() {
        return new ChannelPreference(true, true, true, true, true);
    }

    private static Map<String, ChannelPreference> defaultMatrix() {
        Map<String, ChannelPreference> map = new HashMap<>();
        ChannelPreference all = allTrue();
        ChannelPreference inAppWs = new ChannelPreference(false, false, false, true, true);
        ChannelPreference invitation = new ChannelPreference(true, false, false, true, true);
        ChannelPreference grabber = new ChannelPreference(true, true, true, true, true);

        map.put("publication.failed", all);
        map.put("publication.succeeded", inAppWs);
        map.put("channel.reauth_required", all);
        map.put("channel.no_permissions", all);
        map.put("grabber.moderation_queued", grabber);
        map.put("grabber.setting_error", grabber);
        map.put("ord.error", all);
        map.put("ord.erid_failed", all);
        map.put("ord.problem", all);
        map.put("ord.expiry_warning", all);
        map.put("ord.display_ended", all);
        map.put("invitation.created", invitation);
        map.put("team.approval_requested", all);
        return map;
    }
}
