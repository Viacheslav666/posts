package ru.posts.emulator.connect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConnectRedirectValidationTest {

    @Test
    void allowsPostsChannelCallback() {
        assertThat(ConnectApiController.isAllowedChannelRedirect(
                        "http://100.102.9.56:3000/app/ws/channels/connect/vk/callback"))
                .isTrue();
    }

    @Test
    void rejectsJavascriptScheme() {
        assertThat(ConnectApiController.isAllowedChannelRedirect("javascript:alert(1)"))
                .isFalse();
    }

    @Test
    void rejectsForeignPath() {
        assertThat(ConnectApiController.isAllowedChannelRedirect("https://evil.example/steal"))
                .isFalse();
    }
}
