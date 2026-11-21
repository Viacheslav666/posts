package ru.posts.api.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import ru.posts.api.web.dto.post.PostDtos;

class PostVariantOptionsSanitizerTest {

    @Test
    void stripsUnpinWhenPinIsOff() {
        Instant unpin = Instant.parse("2026-12-01T12:00:00Z");
        var options = new PostDtos.PostVariantOptions(
                false, unpin, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        var sanitized = PostVariantOptionsSanitizer.stripUnpinWithoutPin(options);
        assertThat(sanitized.unpinAt()).isNull();
    }
}
