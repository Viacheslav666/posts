package ru.posts.social.vk;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.posts.social.api.ReconciliationMode;

@ConfigurationProperties(prefix = "posts.social.vk")
public class VkAdapterProperties {

    private String baseUrl = "https://api.vk.com";
    private String tokenUrl = "https://id.vk.com/oauth2/auth";
    private String apiVersion = "5.199";
    private Duration readTimeout = Duration.ofSeconds(15);
    /** {@code emulator} for local/IT against social-emulator; {@code api} for production. */
    private String reconciliationMode = "api";
    /** How many recent wall posts (incl. postponed) to scan on UNKNOWN_OUTCOME. */
    private int findExistingScanLimit = 50;
    /** ± window in seconds when matching content fingerprint on wall.get. */
    private long findExistingTimeWindowSeconds = 120;

    public String baseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String tokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String apiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String reconciliationMode() {
        return reconciliationMode;
    }

    public void setReconciliationMode(String reconciliationMode) {
        this.reconciliationMode = reconciliationMode;
    }

    public ReconciliationMode reconciliation() {
        return ReconciliationMode.from(reconciliationMode);
    }

    public int findExistingScanLimit() {
        return findExistingScanLimit;
    }

    public void setFindExistingScanLimit(int findExistingScanLimit) {
        this.findExistingScanLimit = findExistingScanLimit;
    }

    public long findExistingTimeWindowSeconds() {
        return findExistingTimeWindowSeconds;
    }

    public void setFindExistingTimeWindowSeconds(long findExistingTimeWindowSeconds) {
        this.findExistingTimeWindowSeconds = findExistingTimeWindowSeconds;
    }
}
