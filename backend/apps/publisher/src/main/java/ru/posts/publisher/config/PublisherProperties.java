package ru.posts.publisher.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.publisher")
public class PublisherProperties {

    private String network = "tg";
    private String podName = "publisher-local";
    private Duration claimLockTtl = Duration.ofMinutes(5);
    private int prefetch = 50;
    private int maxAttempts = 8;
    private Duration maxAgeAfterRunAt = Duration.ofMinutes(60);

    public String network() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public String podName() {
        return podName;
    }

    public void setPodName(String podName) {
        this.podName = podName;
    }

    public Duration claimLockTtl() {
        return claimLockTtl;
    }

    public void setClaimLockTtl(Duration claimLockTtl) {
        this.claimLockTtl = claimLockTtl;
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

    public Duration maxAgeAfterRunAt() {
        return maxAgeAfterRunAt;
    }

    public void setMaxAgeAfterRunAt(Duration maxAgeAfterRunAt) {
        this.maxAgeAfterRunAt = maxAgeAfterRunAt;
    }
}
