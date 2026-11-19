package ru.posts.social.api;

import java.time.Instant;
import java.util.Map;

/** Optional fields for moderation / suggest actions (BE-33). */
public record ModerationPayload(
        String text,
        Boolean signed,
        Boolean fromGroup,
        Instant banEndDate,
        Integer banReason,
        String banComment,
        Boolean banCommentVisible,
        String messageText,
        String replyToCommentId,
        Map<String, Object> extra) {

    public ModerationPayload {
        extra = extra == null ? Map.of() : Map.copyOf(extra);
    }

    public static ModerationPayload empty() {
        return new ModerationPayload(null, null, null, null, null, null, null, null, null, Map.of());
    }

    public static ModerationPayload text(String text) {
        return new ModerationPayload(text, null, null, null, null, null, null, null, null, Map.of());
    }
}
