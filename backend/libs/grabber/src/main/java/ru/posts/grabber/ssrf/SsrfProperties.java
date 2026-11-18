package ru.posts.grabber.ssrf;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.grabber.ssrf")
public class SsrfProperties {

    /** Hosts allowed even if they resolve to loopback (WireMock in tests). */
    private List<String> allowedHosts = new ArrayList<>();

    private int connectTimeoutMs = SsrfGuard.DEFAULT_CONNECT_TIMEOUT_MS;

    private int readTimeoutMs = SsrfGuard.DEFAULT_READ_TIMEOUT_MS;

    private int maxBytes = SsrfGuard.DEFAULT_MAX_BYTES;

    private int maxRedirects = SsrfGuard.DEFAULT_MAX_REDIRECTS;

    public List<String> getAllowedHosts() {
        return allowedHosts;
    }

    public void setAllowedHosts(List<String> allowedHosts) {
        this.allowedHosts = allowedHosts == null ? new ArrayList<>() : allowedHosts;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }

    public void setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
    }
}
