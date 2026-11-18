package ru.posts.ord.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** QA-13: sandbox URL guard must refuse production ORD hosts. */
class OrdSandboxGuardTest {

    @Test
    @DisplayName("VK sandbox URL is accepted")
    void vkSandboxAccepted() {
        assertThatCode(() -> OrdSandboxGuard.assertVkOrdSandboxBaseUrl("https://api-sandbox.ord.vk.com"))
                .doesNotThrowAnyException();
        assertThatCode(() -> OrdSandboxGuard.assertVkOrdSandboxBaseUrl("https://api-sandbox.ord.vk.com/"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("VK production URL is rejected (ЕРИР risk)")
    void vkProductionRejected() {
        assertThatThrownBy(() -> OrdSandboxGuard.assertVkOrdSandboxBaseUrl("https://api.ord.vk.com"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("api.ord.vk.com");
        assertThatThrownBy(() -> OrdSandboxGuard.assertVkOrdSandboxBaseUrl("https://api.ord.vk.com/v1"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("VK blank / non-sandbox URL is rejected")
    void vkBlankAndRandomRejected() {
        assertThatThrownBy(() -> OrdSandboxGuard.assertVkOrdSandboxBaseUrl(" ")).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> OrdSandboxGuard.assertVkOrdSandboxBaseUrl("https://example.com"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("api-sandbox.ord.vk.com");
    }

    @Test
    @DisplayName("Yandex prestable accepted; production rejected")
    void yandexPrestableOnly() {
        assertThatCode(() -> OrdSandboxGuard.assertYandexOrdPrestableBaseUrl("https://ord-prestable.yandex.net"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> OrdSandboxGuard.assertYandexOrdPrestableBaseUrl("https://ord.yandex.net"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ord.yandex.net");
    }
}
