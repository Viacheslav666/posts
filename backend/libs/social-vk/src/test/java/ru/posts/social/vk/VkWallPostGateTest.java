package ru.posts.social.vk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VkWallPostGateTest {

    @Test
    void fromGroupOnlyForCommunity() {
        assertThat(VkWallPostGate.postFromGroup("-100", null)).isTrue();
        assertThat(VkWallPostGate.postFromGroup("100", "group")).isTrue();
        assertThat(VkWallPostGate.postFromGroup("-100", "page")).isTrue();
        assertThat(VkWallPostGate.postFromGroup("1001", "profile")).isFalse();
        assertThat(VkWallPostGate.postFromGroup("1001", null)).isFalse();
        assertThat(VkWallPostGate.postFromGroup("-100", "profile")).isFalse();
    }

    @Test
    void vkIdTokenOnProfileCannotPost() {
        assertThat(VkWallPostGate.cannotPost("vk2.a.user-token", "1001", "profile"))
                .isTrue();
        assertThat(VkWallPostGate.cannotPost("vk2.a.user-token", "1001", null)).isTrue();
        assertThat(VkWallPostGate.cannotPost("vk2.a.user-token", "-100", "group"))
                .isFalse();
        assertThat(VkWallPostGate.cannotPost("classic-oauth-token", "1001", "profile"))
                .isFalse();
        assertThat(VkWallPostGate.cannotPost("vk1.a.community", "-100", "group"))
                .isFalse();
    }

    @Test
    void prior1051BlocksUntilReconnect() {
        assertThat(VkWallPostGate.blocked("classic-oauth-token", "-100", "group", "no_permissions"))
                .isTrue();
        assertThat(VkWallPostGate.blocked("classic-oauth-token", "-100", "group", "active"))
                .isFalse();
        assertThat(VkWallPostGate.blocked("vk2.a.user-token", "1001", "profile", "active"))
                .isTrue();
    }
}
