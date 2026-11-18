package ru.posts.ord.reporting.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.ord.reports")
public class OrdReportingProperties {

    /** Cron for monthly enqueue+run (Europe/Moscow). Default: 7th day 03:00. */
    private String cron = "0 0 3 7 * *";

    /** Days before display/contract end to warn workspace owner (default 3). */
    private int expiryWarningDays = 3;

    /** Max submit attempts before permanent error. */
    private int maxAttempts = 5;

    private Duration retryBackoff = Duration.ofHours(1);

    /**
     * When set (tests), every creative uses this impression count instead of social APIs.
     * Production must leave null.
     */
    private Long impressionsOverride;

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public int getExpiryWarningDays() {
        return expiryWarningDays;
    }

    public void setExpiryWarningDays(int expiryWarningDays) {
        this.expiryWarningDays = expiryWarningDays;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(Duration retryBackoff) {
        this.retryBackoff = retryBackoff;
    }

    public Long getImpressionsOverride() {
        return impressionsOverride;
    }

    public void setImpressionsOverride(Long impressionsOverride) {
        this.impressionsOverride = impressionsOverride;
    }
}
