package ru.posts.social.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialLiveApiGuardTest {

    @Test
    @DisplayName("emulator / localhost URLs allowed")
    void allowsEmulator() {
        assertThatCode(() -> SocialLiveApiGuard.assertNotProductionSocialUrl("http://127.0.0.1:8090/method/wall.post"))
                .doesNotThrowAnyException();
        assertThatCode(() -> SocialLiveApiGuard.assertNotProductionSocialUrl("http://localhost/bot"))
                .doesNotThrowAnyException();
        assertThat(SocialLiveApiGuard.isForbiddenProductionHost("http://127.0.0.1:18090"))
                .isFalse();
    }

    @Test
    @DisplayName("production VK/TG/Max/OK hosts rejected")
    void rejectsProduction() {
        assertThatThrownBy(() -> SocialLiveApiGuard.assertNotProductionSocialUrl("https://api.vk.com/method/wall.get"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("api.vk.com");
        assertThatThrownBy(() -> SocialLiveApiGuard.assertNotProductionSocialUrl(
                        "https://api.telegram.org/botTOKEN/sendMessage"))
                .hasMessageContaining("api.telegram.org");
        assertThatThrownBy(
                        () -> SocialLiveApiGuard.assertNotProductionSocialUrl("https://platform-api2.max.ru/messages"))
                .hasMessageContaining("platform-api2.max.ru");
        assertThatThrownBy(() -> SocialLiveApiGuard.assertNotProductionSocialUrl("https://api.ok.ru/fb.do"))
                .hasMessageContaining("api.ok.ru");
        assertThat(SocialLiveApiGuard.isForbiddenProductionHost("https://api.vk.com"))
                .isTrue();
    }
}
