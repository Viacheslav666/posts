package ru.posts.notifications.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.notifications")
public class NotificationsProperties {

    private String appBaseUrl = "http://localhost:3000";
    private int prefetch = 50;
    private int maxAttempts = 8;
    private int retryBatchSize = 100;
    private long retryPollIntervalMs = 2000;
    private final Mail mail = new Mail();
    private final Telegram telegram = new Telegram();
    private final WebPush webPush = new WebPush();
    private final RateLimits rateLimits = new RateLimits();

    public String appBaseUrl() {
        return appBaseUrl;
    }

    public void setAppBaseUrl(String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    public int prefetch() {
        return prefetch;
    }

    public void setPrefetch(int prefetch) {
        this.prefetch = prefetch;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int retryBatchSize() {
        return retryBatchSize;
    }

    public void setRetryBatchSize(int retryBatchSize) {
        this.retryBatchSize = retryBatchSize;
    }

    public long retryPollIntervalMs() {
        return retryPollIntervalMs;
    }

    public void setRetryPollIntervalMs(long retryPollIntervalMs) {
        this.retryPollIntervalMs = retryPollIntervalMs;
    }

    public Mail mail() {
        return mail;
    }

    public Telegram telegram() {
        return telegram;
    }

    public WebPush webPush() {
        return webPush;
    }

    public RateLimits rateLimits() {
        return rateLimits;
    }

    public static final class Mail {
        private String from = "noreply@posts.local";
        private String fromName = "Posts";

        public String from() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String fromName() {
            return fromName;
        }

        public void setFromName(String fromName) {
            this.fromName = fromName;
        }
    }

    public static final class Telegram {
        private String botToken = "";
        private String botUsername = "";
        private String webhookSecret = "";
        private String apiBaseUrl = "https://api.telegram.org";

        public String botToken() {
            return botToken;
        }

        public void setBotToken(String botToken) {
            this.botToken = botToken;
        }

        public String botUsername() {
            return botUsername;
        }

        public void setBotUsername(String botUsername) {
            this.botUsername = botUsername;
        }

        public String webhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public String apiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }
    }

    public static final class WebPush {
        private String vapidPublicKey = "";
        private String vapidPrivateKey = "";
        private String vapidSubject = "mailto:noreply@posts.local";

        public String vapidPublicKey() {
            return vapidPublicKey;
        }

        public void setVapidPublicKey(String vapidPublicKey) {
            this.vapidPublicKey = vapidPublicKey;
        }

        public String vapidPrivateKey() {
            return vapidPrivateKey;
        }

        public void setVapidPrivateKey(String vapidPrivateKey) {
            this.vapidPrivateKey = vapidPrivateKey;
        }

        public String vapidSubject() {
            return vapidSubject;
        }

        public void setVapidSubject(String vapidSubject) {
            this.vapidSubject = vapidSubject;
        }

        public boolean isConfigured() {
            return vapidPublicKey != null
                    && !vapidPublicKey.isBlank()
                    && vapidPrivateKey != null
                    && !vapidPrivateKey.isBlank();
        }
    }

    public static final class RateLimits {
        private int telegramPerSecond = 25;
        private Duration telegramMinInterval = Duration.ofMillis(40);

        public int telegramPerSecond() {
            return telegramPerSecond;
        }

        public void setTelegramPerSecond(int telegramPerSecond) {
            this.telegramPerSecond = telegramPerSecond;
        }

        public Duration telegramMinInterval() {
            return telegramMinInterval;
        }

        public void setTelegramMinInterval(Duration telegramMinInterval) {
            this.telegramMinInterval = telegramMinInterval;
        }
    }
}
