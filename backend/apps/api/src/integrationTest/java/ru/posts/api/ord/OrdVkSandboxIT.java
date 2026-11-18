package ru.posts.api.ord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ru.posts.ord.api.OrdCredentials;
import ru.posts.ord.api.OrdSandboxGuard;
import ru.posts.ord.vk.OrdVkProperties;
import ru.posts.ord.vk.VkOrdProvider;

/**
 * Live VK ORD sandbox smoke (https://api-sandbox.ord.vk.com). Skips when {@code
 * VK_ORD_SANDBOX_API_KEY} is absent (forks / PR CI). Never targets production {@code
 * api.ord.vk.com} — {@link OrdSandboxGuard} fails the test if the base URL is not sandbox. Does not
 * log the API key.
 */
@Tag("ord-sandbox")
class OrdVkSandboxIT {

    private static final String SANDBOX_BASE = "https://api-sandbox.ord.vk.com";

    @Test
    @DisplayName("VK ORD sandbox: testConnection with Bearer key from env")
    void sandboxTestConnection() {
        String apiKey = System.getenv("VK_ORD_SANDBOX_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "VK_ORD_SANDBOX_API_KEY not set — skipping");

        // Hard fail (not skip) if anyone points this IT at production ЕРИР.
        OrdSandboxGuard.assertVkOrdSandboxBaseUrl(SANDBOX_BASE);

        OrdVkProperties properties = new OrdVkProperties();
        properties.setBaseUrl(SANDBOX_BASE);
        OrdSandboxGuard.assertVkOrdSandboxBaseUrl(properties.baseUrl());
        assertThat(properties.baseUrl()).contains(OrdSandboxGuard.VK_SANDBOX_HOST);

        VkOrdProvider provider = new VkOrdProvider(properties, new ObjectMapper());
        provider.testConnection(new OrdCredentials(apiKey));
    }

    @Test
    @DisplayName("QA-13 guard: production VK ORD URL is rejected even without secret")
    void guardRejectsProductionUrl() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> OrdSandboxGuard.assertVkOrdSandboxBaseUrl("https://api.ord.vk.com"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(OrdSandboxGuard.VK_PRODUCTION_HOST);
    }
}
