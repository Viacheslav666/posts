package ru.posts.social.vk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VkFailureLogTest {

    @Test
    void redactStripsTokens() {
        String raw = "failed access_token=vk2.a.SECRET.token and vk1.a.OTHER";
        String redacted = VkFailureLog.redact(raw);
        assertThat(redacted).doesNotContain("SECRET");
        assertThat(redacted).doesNotContain("vk2.a.");
        assertThat(redacted).doesNotContain("vk1.a.");
        assertThat(redacted).contains("access_token=[redacted]");
        assertThat(redacted).contains("[redacted]");
    }
}
