package ru.posts.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class VkStringAttachmentValidatorTest {

    @Test
    void acceptsCommonVkTokens() {
        assertThat(VkStringAttachmentValidator.isValid("photo-1_2")).isTrue();
        assertThat(VkStringAttachmentValidator.isValid("photo-79668981_457342016"))
                .isTrue();
        assertThat(VkStringAttachmentValidator.isValid("video123_456")).isTrue();
        assertThat(VkStringAttachmentValidator.isValid("doc-10_20_accessKey")).isTrue();
        assertThat(VkStringAttachmentValidator.isValid("poll-100_5")).isTrue();
        assertThat(VkStringAttachmentValidator.isValid("clip-1_2")).isTrue();
    }

    @Test
    void rejectsMalformed() {
        assertThat(VkStringAttachmentValidator.isValid("photo")).isFalse();
        assertThat(VkStringAttachmentValidator.isValid("photo-_1")).isFalse();
        assertThat(VkStringAttachmentValidator.isValid("unknown-1_2")).isFalse();
        assertThat(VkStringAttachmentValidator.isValid("javascript:alert(1)")).isFalse();
    }

    @Test
    void acceptsHttpLinkSnippets() {
        assertThat(VkStringAttachmentValidator.isValid("https://vk.com/@club-article"))
                .isTrue();
        assertThat(VkStringAttachmentValidator.isValid("http://example.com/a_1?b=2"))
                .isTrue();
        assertThat(VkStringAttachmentValidator.isValid("ftp://example.com")).isFalse();
        assertThat(VkStringAttachmentValidator.normalize(List.of("https://Example.com/A1_2")))
                .containsExactly("https://Example.com/A1_2");
    }

    @Test
    void invalidOfCollectsBadTokens() {
        assertThat(VkStringAttachmentValidator.invalidOf(List.of("photo-1_2", "bad", "video-3_4")))
                .containsExactly("bad");
    }
}
