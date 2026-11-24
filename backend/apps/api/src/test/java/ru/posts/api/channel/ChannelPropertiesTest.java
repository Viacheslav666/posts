package ru.posts.api.channel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChannelPropertiesTest {

    @Test
    void buildsWorkspaceScopedOAuthRedirect() {
        ChannelProperties props = new ChannelProperties();
        props.setAppBaseUrl("http://100.102.9.56:3000");
        UUID ws = UUID.fromString("11111111-1111-7111-8111-111111111111");
        assertThat(props.channelConnectRedirectUri("vk", ws))
                .isEqualTo(
                        "http://100.102.9.56:3000/app/11111111-1111-7111-8111-111111111111/channels/connect/vk/callback");
    }

    @Test
    void usesExplicitRedirectOverrideWhenSet() {
        ChannelProperties props = new ChannelProperties();
        props.setVkRedirectUri("http://example.test/callback");
        UUID ws = UUID.randomUUID();
        assertThat(props.channelConnectRedirectUri("vk", ws)).isEqualTo("http://example.test/callback");
    }

    @Test
    void publicAuthorizationUsesSocialPublicBase() {
        ChannelProperties props = new ChannelProperties();
        props.setSocialBaseUrl("http://social-emulator:8090");
        props.setSocialPublicBaseUrl("http://100.102.9.56:3000/emulator");
        assertThat(props.vkAuthorizationUri()).isEqualTo("http://100.102.9.56:3000/emulator/oauth/vk/authorize");
    }

    @Test
    void vkIdModeUsesRealApiBaseAndScopesWithoutOffline() {
        ChannelProperties props = new ChannelProperties();
        props.setVkTokenUri("https://id.vk.ru/oauth2/auth");
        props.setVkAuthorizationUri("https://id.vk.ru/authorize");
        assertThat(props.vkIdMode()).isTrue();
        assertThat(props.vkApiBaseUrl()).isEqualTo("https://api.vk.com");
        assertThat(props.vkScopes()).isEqualTo("groups wall");
    }

    @Test
    void emulatorModeKeepsSocialBaseForVkApi() {
        ChannelProperties props = new ChannelProperties();
        props.setSocialBaseUrl("http://127.0.0.1:8090");
        assertThat(props.vkIdMode()).isFalse();
        assertThat(props.vkApiBaseUrl()).isEqualTo("http://127.0.0.1:8090");
        assertThat(props.vkScopes()).contains("offline");
    }
}
