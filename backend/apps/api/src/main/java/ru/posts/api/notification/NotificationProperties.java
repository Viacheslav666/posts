package ru.posts.api.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.notifications")
public class NotificationProperties {

    private String telegramBotUsername = "posts_notify_bot";
    private String telegramWebhookSecret = "local-notify-tg-secret";
    private String vapidPublicKey = "";
    private int telegramLinkCodeTtlMinutes = 15;
    private int unsubscribeTokenTtlDays = 30;

    public String telegramBotUsername() {
        return telegramBotUsername;
    }

    public void setTelegramBotUsername(String telegramBotUsername) {
        this.telegramBotUsername = telegramBotUsername;
    }

    public String telegramWebhookSecret() {
        return telegramWebhookSecret;
    }

    public void setTelegramWebhookSecret(String telegramWebhookSecret) {
        this.telegramWebhookSecret = telegramWebhookSecret;
    }

    public String vapidPublicKey() {
        return vapidPublicKey;
    }

    public void setVapidPublicKey(String vapidPublicKey) {
        this.vapidPublicKey = vapidPublicKey;
    }

    public int telegramLinkCodeTtlMinutes() {
        return telegramLinkCodeTtlMinutes;
    }

    public void setTelegramLinkCodeTtlMinutes(int telegramLinkCodeTtlMinutes) {
        this.telegramLinkCodeTtlMinutes = telegramLinkCodeTtlMinutes;
    }

    public int unsubscribeTokenTtlDays() {
        return unsubscribeTokenTtlDays;
    }

    public void setUnsubscribeTokenTtlDays(int unsubscribeTokenTtlDays) {
        this.unsubscribeTokenTtlDays = unsubscribeTokenTtlDays;
    }
}
