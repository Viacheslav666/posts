package ru.posts.notifications.pipeline;

import java.util.Set;

public final class NotificationChannels {

    public static final String EMAIL = "email";
    public static final String TELEGRAM = "telegram";
    public static final String WEB_PUSH = "web_push";
    public static final String IN_APP = "in_app";
    public static final String WS = "ws";

    public static final Set<String> ALL = Set.of(EMAIL, TELEGRAM, WEB_PUSH, IN_APP, WS);
    public static final Set<String> QUEUE_CHANNELS = Set.of(EMAIL, TELEGRAM, WEB_PUSH, WS);

    public static final Set<String> CRITICAL_TYPES =
            Set.of("publication.failed", "channel.reauth_required", "channel.no_permissions");

    private NotificationChannels() {}

    public static boolean isCritical(String eventType) {
        return eventType != null && CRITICAL_TYPES.contains(eventType);
    }
}
