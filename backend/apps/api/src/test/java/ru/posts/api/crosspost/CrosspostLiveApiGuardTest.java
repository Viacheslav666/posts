package ru.posts.api.crosspost;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BE-36: unit/integration tests must never target live social API hosts.
 *
 * <p>Production defaults live in application YAML; tests/emulator must override base URLs.
 */
class CrosspostLiveApiGuardTest {

    @Test
    @DisplayName("live API guard: default test overrides must not contain production hosts")
    void noProductionHosts() {
        assertDoesNotContain("posts.vk.api-base-url", "api.vk.com");
        assertDoesNotContain("posts.telegram.api-base-url", "api.telegram.org");
        assertDoesNotContain("posts.max.api-base-url", "platform-api2.max.ru");
        assertDoesNotContain("posts.ok.api-base-url", "api.ok.ru");
        assertDoesNotContain("posts.channels.social-base-url", "api.vk.com");
        assertDoesNotContain("posts.social-inbound.social-base-url", "api.telegram.org");
    }

    private static void assertDoesNotContain(String property, String forbidden) {
        String value = System.getProperty(property, System.getenv().getOrDefault(property.replace('.', '_'), ""));
        if (value == null || value.isBlank()) {
            return;
        }
        assertThat(value.toLowerCase(Locale.ROOT)).doesNotContain(forbidden.toLowerCase(Locale.ROOT));
    }
}
