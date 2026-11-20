package ru.posts.api.post;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.post.PostDtos;

/** Validates post variant options against schedule metadata. */
public final class PostVariantOptionsValidator {

    private PostVariantOptionsValidator() {}

    /**
     * When {@code pin=true} and {@code unpinAt} is set, {@code unpinAt} must be strictly after the post's
     * base {@code scheduledAt}.
     */
    public static void validateUnpinAt(Instant scheduledAt, PostDtos.PostVariantOptions options) {
        if (options == null || options.unpinAt() == null) {
            return;
        }
        if (!Boolean.TRUE.equals(options.pin())) {
            return;
        }
        if (scheduledAt == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "unpinAt requires scheduledAt on the post");
        }
        if (!options.unpinAt().isAfter(scheduledAt)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ProblemCodes.VALIDATION_FAILED,
                    "unpinAt must be after scheduled publish time");
        }
    }
}
