package ru.posts.api.ord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ru.posts.ord.api.OrdCredentials;
import ru.posts.ord.vk.OrdVkProperties;
import ru.posts.ord.vk.VkOrdProvider;

/**
 * Live VK ORD sandbox smoke (https://api-sandbox.ord.vk.com). Skips when {@code
 * VK_ORD_SANDBOX_API_KEY} is absent. Never targets production {@code api.ord.vk.com}. Does not log
 * the API key.
 */
@Tag("ord-sandbox")
class OrdVkSandboxIT {

    @Test
    @DisplayName("VK ORD sandbox: testConnection with Bearer key from env")
    void sandboxTestConnection() {
        String apiKey = System.getenv("VK_ORD_SANDBOX_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "VK_ORD_SANDBOX_API_KEY not set — skipping");

        OrdVkProperties properties = new OrdVkProperties();
        properties.setBaseUrl("https://api-sandbox.ord.vk.com");
        assertThat(properties.baseUrl()).contains("api-sandbox.ord.vk.com");
        assertThat(properties.baseUrl()).doesNotContain("api.ord.vk.com");

        VkOrdProvider provider = new VkOrdProvider(properties, new ObjectMapper());
        provider.testConnection(new OrdCredentials(apiKey));
    }
}
