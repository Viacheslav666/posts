package ru.posts.emulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Emulator knobs for IT/e2e. {@code publicBaseUrl} must match the actual listen address so VK/OK
 * upload_url callbacks reach this process (ephemeral ports in pipeline IT).
 */
@ConfigurationProperties(prefix = "posts.emulator")
public class EmulatorProperties {

    /** Absolute base URL advertised in upload_url responses, e.g. {@code http://127.0.0.1:18090}. */
    private String publicBaseUrl = "http://localhost:8090";

    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl == null || publicBaseUrl.isBlank() ? "http://localhost:8090" : publicBaseUrl;
    }

    public String absolute(String path) {
        String base =
                publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        String p = path.startsWith("/") ? path : "/" + path;
        return base + p;
    }
}
