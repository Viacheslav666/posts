package ru.posts.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SocialHooksSecretTest {

    @Test
    void constantTimeEqualsRejectsNullAndMismatch() {
        assertThat(SocialHooksController.constantTimeEquals(null, "a")).isFalse();
        assertThat(SocialHooksController.constantTimeEquals("a", null)).isFalse();
        assertThat(SocialHooksController.constantTimeEquals("secret", "secret")).isTrue();
        assertThat(SocialHooksController.constantTimeEquals("secret", "secreX")).isFalse();
    }
}
