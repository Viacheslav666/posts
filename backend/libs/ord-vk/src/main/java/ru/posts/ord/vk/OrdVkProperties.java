package ru.posts.ord.vk;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.ord.vk")
public class OrdVkProperties {

    /** Default sandbox base URL; production is {@code https://api.ord.vk.com}. */
    private String baseUrl = "https://api-sandbox.ord.vk.com";

    private Duration readTimeout = Duration.ofSeconds(15);

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
}
