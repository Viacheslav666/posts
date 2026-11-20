package ru.posts.emulator.connect;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OAuthCallbackRedirectTest {

    @Test
    void buildsRedirectForAllowedCallback() {
        String redirect = "http://100.102.9.56:3000/app/ws/channels/connect/vk/callback?existing=1";
        Optional<URI> target = OAuthCallbackRedirect.callbackUri(
                EmulatorConnectProperties.withBases("http://100.102.9.56:3000").allowedAppBaseUrls(),
                redirect,
                "vk-admin",
                "state-1");
        assertThat(target).isPresent();
        assertThat(target.get().toString())
                .isEqualTo("http://100.102.9.56:3000/app/ws/channels/connect/vk/callback?code=vk-admin&state=state-1");
    }

    @Test
    void rejectsForeignBase() {
        String redirect = "https://evil.example/app/ws/channels/connect/vk/callback";
        assertThat(OAuthCallbackRedirect.callbackUri(
                        EmulatorConnectProperties.withBases("http://localhost:3000")
                                .allowedAppBaseUrls(),
                        redirect,
                        "vk-admin",
                        "y"))
                .isEmpty();
    }

    @Test
    void rejectsDisallowedPath() {
        assertThat(OAuthCallbackRedirect.callbackUri(
                        EmulatorConnectProperties.withBases("http://localhost:3000")
                                .allowedAppBaseUrls(),
                        "https://evil.example/steal",
                        "vk-admin",
                        "y"))
                .isEmpty();
    }
}
