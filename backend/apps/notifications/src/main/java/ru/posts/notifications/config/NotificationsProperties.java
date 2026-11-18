package ru.posts.notifications.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "posts.notifications")
public class NotificationsProperties {

    private String appBaseUrl = "http://localhost:3000";
    private int prefetch = 50;
    private int maxAttempts = 8;
    private int retryBatchSize = 100;
    private long retryPollIntervalMs = 2000;

    @NestedConfigurationProperty
    private final Mail mail = new Mail();

    @NestedConfigurationProperty
    private final Telegram telegram = new Telegram();

    @NestedConfigurationProperty
    private final WebPush webPush = new WebPush();

    @NestedConfigurationProperty
    private final RateLimits rateLimits = new RateLimits();

    public String getAppBaseUrl() {
        return appBaseUrl;
    }

    /** Alias used by delivery code. */
    public String appBaseUrl() {
        return getAppBaseUrl();
    }

    public void setAppBaseUrl(String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    public int getPrefetch() {
        return prefetch;
    }

    public int prefetch() {
        return getPrefetch();
    }

    public void setPrefetch(int prefetch) {
        this.prefetch = prefetch;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int maxAttempts() {
        return getMaxAttempts();
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getRetryBatchSize() {
        return retryBatchSize;
    }

    public int retryBatchSize() {
        return getRetryBatchSize();
    }

    public void setRetryBatchSize(int retryBatchSize) {
        this.retryBatchSize = retryBatchSize;
    }

    public long getRetryPollIntervalMs() {
        return retryPollIntervalMs;
    }

    public long retryPollIntervalMs() {
        return getRetryPollIntervalMs();
    }

    public void setRetryPollIntervalMs(long retryPollIntervalMs) {
        this.retryPollIntervalMs = retryPollIntervalMs;
    }

    public Mail getMail() {
        return mail;
    }

    public Mail mail() {
        return getMail();
    }

    public Telegram getTelegram() {
        return telegram;
    }

    public Telegram telegram() {
        return getTelegram();
    }

    public WebPush getWebPush() {
        return webPush;
    }

    public WebPush webPush() {
        return getWebPush();
    }

    public RateLimits getRateLimits() {
        return rateLimits;
    }

    public RateLimits rateLimits() {
        return getRateLimits();
    }

    public static final class Mail {
        private String from = "noreply@posts.local";
        private String fromName = "Posts";

        public String getFrom() {
            return from;
        }

        public String from() {
            return getFrom();
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getFromName() {
            return fromName;
        }

        public String fromName() {
            return getFromName();
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

        public String getBotToken() {
            return botToken;
        }

        public String botToken() {
            return getBotToken();
        }

        public void setBotToken(String botToken) {
            this.botToken = botToken;
        }

        public String getBotUsername() {
            return botUsername;
        }

        public String botUsername() {
            return getBotUsername();
        }

        public void setBotUsername(String botUsername) {
            this.botUsername = botUsername;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public String webhookSecret() {
            return getWebhookSecret();
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public String apiBaseUrl() {
            return getApiBaseUrl();
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }
    }

    public static final class WebPush {
        private String vapidPublicKey = "";
        private String vapidPrivateKey = "";
        private String vapidSubject = "mailto:noreply@posts.local";

        public String getVapidPublicKey() {
            return vapidPublicKey;
        }

        public String vapidPublicKey() {
            return getVapidPublicKey();
        }

        public void setVapidPublicKey(String vapidPublicKey) {
            this.vapidPublicKey = vapidPublicKey;
        }

        public String getVapidPrivateKey() {
            return vapidPrivateKey;
        }

        public String vapidPrivateKey() {
            return getVapidPrivateKey();
        }

        public void setVapidPrivateKey(String vapidPrivateKey) {
            this.vapidPrivateKey = vapidPrivateKey;
        }

        public String getVapidSubject() {
            return vapidSubject;
        }

        public String vapidSubject() {
            return getVapidSubject();
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

        public int getTelegramPerSecond() {
            return telegramPerSecond;
        }

        public int telegramPerSecond() {
            return getTelegramPerSecond();
        }

        public void setTelegramPerSecond(int telegramPerSecond) {
            this.telegramPerSecond = telegramPerSecond;
        }

        public Duration getTelegramMinInterval() {
            return telegramMinInterval;
        }

        public Duration telegramMinInterval() {
            return getTelegramMinInterval();
        }

        public void setTelegramMinInterval(Duration telegramMinInterval) {
            this.telegramMinInterval = telegramMinInterval;
        }
    }
}
