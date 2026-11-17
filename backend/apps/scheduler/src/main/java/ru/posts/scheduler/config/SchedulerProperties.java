package ru.posts.scheduler.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.scheduler")
public class SchedulerProperties {

    private int batchSize = 500;
    private Duration lockTtl = Duration.ofMinutes(10);
    private Duration lookAhead = Duration.ofSeconds(5);
    private Duration confirmTimeout = Duration.ofSeconds(5);
    private long dispatchIntervalMs = 1000;
    private long reaperIntervalMs = 30_000;

    public int batchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration lockTtl() {
        return lockTtl;
    }

    public void setLockTtl(Duration lockTtl) {
        this.lockTtl = lockTtl;
    }

    public Duration lookAhead() {
        return lookAhead;
    }

    public void setLookAhead(Duration lookAhead) {
        this.lookAhead = lookAhead;
    }

    public Duration confirmTimeout() {
        return confirmTimeout;
    }

    public void setConfirmTimeout(Duration confirmTimeout) {
        this.confirmTimeout = confirmTimeout;
    }

    public long dispatchIntervalMs() {
        return dispatchIntervalMs;
    }

    public void setDispatchIntervalMs(long dispatchIntervalMs) {
        this.dispatchIntervalMs = dispatchIntervalMs;
    }

    public long reaperIntervalMs() {
        return reaperIntervalMs;
    }

    public void setReaperIntervalMs(long reaperIntervalMs) {
        this.reaperIntervalMs = reaperIntervalMs;
    }
}
