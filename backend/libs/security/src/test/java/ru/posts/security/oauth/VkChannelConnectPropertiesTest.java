package ru.posts.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VkChannelConnectPropertiesTest {

    @Test
    void vkIdModeMatchesChannelProperties() {
        VkChannelConnectProperties real = new VkChannelConnectProperties();
        real.setVkTokenUri("https://id.vk.ru/oauth2/auth");
        real.setVkAuthorizationUri("https://id.vk.ru/authorize");
        assertThat(real.vkIdMode()).isTrue();

        VkChannelConnectProperties oauthPath = new VkChannelConnectProperties();
        oauthPath.setVkTokenUri("http://127.0.0.1:8090/oauth2/auth");
        oauthPath.setVkAuthorizationUri("http://127.0.0.1:8090/oauth/vk/authorize");
        assertThat(oauthPath.vkIdMode()).isTrue();

        VkChannelConnectProperties emulator = new VkChannelConnectProperties();
        emulator.setSocialBaseUrl("http://127.0.0.1:8090");
        assertThat(emulator.vkIdMode()).isFalse();
    }
}
