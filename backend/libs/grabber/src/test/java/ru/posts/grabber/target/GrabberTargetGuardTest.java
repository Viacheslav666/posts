package ru.posts.grabber.target;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GrabberTargetGuardTest {

    @Test
    void acceptsVkCommunityAndRejectsProfileOrChat() {
        assertThatCode(() -> GrabberTargetGuard.requireCommunity("vk", "group")).doesNotThrowAnyException();
        assertThatCode(() -> GrabberTargetGuard.requireCommunity("VK", "page")).doesNotThrowAnyException();
        assertThatThrownBy(() -> GrabberTargetGuard.requireCommunity("vk", "profile"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("сообщество");
        assertThatThrownBy(() -> GrabberTargetGuard.requireCommunity("vk", "chat"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GrabberTargetGuard.requireCommunity("telegram", "channel"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
