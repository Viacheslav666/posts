package ru.posts.social.ok;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.posts.social.api.ReconciliationMode;

@ConfigurationProperties(prefix = "posts.social.ok")
public class OkAdapterProperties {

    private String baseUrl = "https://api.ok.ru";
    private Duration readTimeout = Duration.ofSeconds(15);
    /** Application key for OK REST signing ({@code application_key}). */
    private String applicationKey = "";
    /** Application secret for {@code sig} (MD5). Empty → skip sig (emulator). */
    private String applicationSecret = "";
    /** {@code emulator} for local/IT; {@code api} for production. */
    private String reconciliationMode = "api";

    private int findExistingScanLimit = 50;
    private long findExistingTimeWindowSeconds = 120;

    public String baseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String applicationKey() {
        return applicationKey;
    }

    public void setApplicationKey(String applicationKey) {
        this.applicationKey = applicationKey;
    }

    public String applicationSecret() {
        return applicationSecret;
    }

    public void setApplicationSecret(String applicationSecret) {
        this.applicationSecret = applicationSecret;
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
