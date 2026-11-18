package ru.posts.api.ord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ru.posts.ord.api.OrdCredentials;
import ru.posts.ord.yandex.OrdYandexProperties;
import ru.posts.ord.yandex.YandexOrdProvider;

/**
 * Live Yandex ORD prestable smoke (https://ord-prestable.yandex.net). Skips when {@code
 * YANDEX_ORD_SANDBOX_TOKEN} is absent. Never targets production {@code ord.yandex.net}. Does not log
 * the token.
 */
@Tag("ord-sandbox")
class OrdYandexSandboxIT {

    @Test
    @DisplayName("Yandex ORD prestable: testConnection with Bearer token from env")
    void sandboxTestConnection() {
        String token = System.getenv("YANDEX_ORD_SANDBOX_TOKEN");
        assumeTrue(token != null && !token.isBlank(), "YANDEX_ORD_SANDBOX_TOKEN not set — skipping");

        OrdYandexProperties properties = new OrdYandexProperties();
        properties.setBaseUrl("https://ord-prestable.yandex.net");
        assertThat(properties.baseUrl()).contains("ord-prestable.yandex.net");
        assertThat(properties.baseUrl()).doesNotContain("://ord.yandex.net");

        YandexOrdProvider provider = new YandexOrdProvider(properties, new ObjectMapper());
        provider.testConnection(new OrdCredentials(token));
    }
}
