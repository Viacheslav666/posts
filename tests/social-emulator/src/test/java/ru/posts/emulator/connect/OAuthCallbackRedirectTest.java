package ru.posts.emulator.connect;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OAuthCallbackRedirectTest {

    @Test
    void buildsRedirectForAllowedCallback() {
        String redirect = "http://100.102.9.56:3000/app/ws/channels/connect/vk/callback?existing=1";
        Optional<URI> uri = OAuthCallbackRedirect.toCallback(redirect, "vk-admin", "state-1");
        assertThat(uri).isPresent();
        assertThat(uri.get().toString())
                .isEqualTo("http://100.102.9.56:3000/app/ws/channels/connect/vk/callback?code=vk-admin&state=state-1");
    }

    @Test
    void rejectsDisallowedTarget() {
        assertThat(OAuthCallbackRedirect.toCallback("https://evil.example/steal", "x", "y"))
                .isEmpty();
    }
}
