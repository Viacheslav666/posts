package ru.posts.social.api;

import java.util.UUID;

/** Per-item outcome from {@link SocialModerationPort#executeBatch}. */
public record ModerationBatchItemResult(
        UUID actionId, boolean success, ModerationResult result, ErrorClass errorClass, String errorMessage) {

    public static ModerationBatchItemResult ok(UUID actionId, ModerationResult result) {
        return new ModerationBatchItemResult(actionId, true, result, null, null);
    }

    public static ModerationBatchItemResult fail(UUID actionId, ErrorClass errorClass, String errorMessage) {
        return new ModerationBatchItemResult(actionId, false, null, errorClass, errorMessage);
    }
}
