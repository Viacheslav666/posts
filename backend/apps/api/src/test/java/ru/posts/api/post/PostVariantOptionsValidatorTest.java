package ru.posts.api.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.posts.api.error.ApiException;
import ru.posts.api.web.dto.post.PostDtos;

class PostVariantOptionsValidatorTest {

    @Test
    void acceptsUnpinAfterScheduledAt() {
        Instant scheduled = Instant.parse("2026-06-01T10:00:00Z");
        var options = new PostDtos.PostVariantOptions(
                true,
                Instant.parse("2026-06-02T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        assertThatCode(() -> PostVariantOptionsValidator.validateUnpinAt(scheduled, options))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsUnpinAtWhenScheduledAtNull() {
        var options = new PostDtos.PostVariantOptions(
                true,
                Instant.parse("2026-06-02T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        assertThatCode(() -> PostVariantOptionsValidator.validateUnpinAt(null, options))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnpinBeforeOrEqualScheduledAt() {
        Instant scheduled = Instant.parse("2026-06-01T10:00:00Z");
        var options = new PostDtos.PostVariantOptions(
                true, scheduled, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null);
        assertThatThrownBy(() -> PostVariantOptionsValidator.validateUnpinAt(scheduled, options))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
