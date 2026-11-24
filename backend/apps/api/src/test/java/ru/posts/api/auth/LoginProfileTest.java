package ru.posts.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoginProfileTest {

    @Test
    void sessionProviderWinsOverIdentityAndPassword() {
        assertThat(LoginProfile.loginMethod("email", List.of("vk"), true)).isEqualTo("email");
        assertThat(LoginProfile.loginMethod("VK", List.of("yandex"), false)).isEqualTo("vk");
    }

    @Test
    void fallsBackToEarliestIdentityThenPassword() {
        assertThat(LoginProfile.loginMethod(null, List.of("vk", "yandex"), true))
                .isEqualTo("vk");
        assertThat(LoginProfile.loginMethod(" ", List.of(), true)).isEqualTo("email");
        assertThat(LoginProfile.loginMethod(null, List.of("unknown"), false)).isNull();
    }

    @Test
    void readsVkAvatarFromUserInfoFields() {
        assertThat(LoginProfile.avatarUrl(Map.of("avatar", "https://sun.vk.example/photo.jpg")))
                .isEqualTo("https://sun.vk.example/photo.jpg");
        assertThat(LoginProfile.avatarUrl(Map.of("photo_200", "https://vk.example/p200.jpg")))
                .isEqualTo("https://vk.example/p200.jpg");
        assertThat(LoginProfile.avatarUrl(Map.of("avatar", "not-a-url"))).isNull();
        assertThat(LoginProfile.avatarUrl(null)).isNull();
    }
}
