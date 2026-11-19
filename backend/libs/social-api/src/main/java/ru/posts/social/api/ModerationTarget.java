package ru.posts.social.api;

import java.util.Map;

/**
 * Remote object addressed by a moderation action.
 *
 * @param commentType wall|photo|video|board|market|discussion|channel_comment|null
 */
public record ModerationTarget(
        String commentType,
        String remotePostId,
        String remoteCommentId,
        String remoteUserId,
        String ownerId,
        Map<String, Object> extra) {

    public ModerationTarget {
        extra = extra == null ? Map.of() : Map.copyOf(extra);
    }

    public ModerationTarget(
            String commentType, String remotePostId, String remoteCommentId, String remoteUserId, String ownerId) {
        this(commentType, remotePostId, remoteCommentId, remoteUserId, ownerId, Map.of());
    }
}
