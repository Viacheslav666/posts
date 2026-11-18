package ru.posts.notifications.pipeline;

/** Enabled delivery channels for one event type after preference + mute resolution. */
public record ChannelPreference(boolean email, boolean telegram, boolean webPush, boolean inApp, boolean ws) {

    public static ChannelPreference none() {
        return new ChannelPreference(false, false, false, false, false);
    }

    public boolean enabled(String channel) {
        return switch (channel) {
            case NotificationChannels.EMAIL -> email;
            case NotificationChannels.TELEGRAM -> telegram;
            case NotificationChannels.WEB_PUSH -> webPush;
            case NotificationChannels.IN_APP -> inApp;
            case NotificationChannels.WS -> ws;
            default -> false;
        };
    }
}
