package ru.posts.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.analytics.posts")
public class AnalyticsProperties {

    /** Cron for incremental monthly materialisation (Europe/Moscow). Default: every 15 minutes. */
    private String cron = "0 */15 * * * *";

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }
}
