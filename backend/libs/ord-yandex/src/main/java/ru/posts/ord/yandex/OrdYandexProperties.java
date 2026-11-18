package ru.posts.ord.yandex;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.ord.yandex")
public class OrdYandexProperties {

    /** Default prestable base URL; production is {@code https://ord.yandex.net}. */
    private String baseUrl = "https://ord-prestable.yandex.net";

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
