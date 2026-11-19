package ru.posts.social.api;

/**
 * Feature keys for {@link NetworkCapabilities#features()} moderation capabilities (BE-33).
 *
 * <p>Values are booleans; absent or false means the network/token cannot perform the action.
 */
public final class ModerationFeatureFlags {

    public static final String PUBLISH_SUGGESTED = "publishSuggested";
    public static final String DELETE_POST = "deletePost";
    public static final String DELETE_COMMENT_WALL = "deleteCommentWall";
    public static final String DELETE_COMMENT_PHOTO = "deleteCommentPhoto";
    public static final String DELETE_COMMENT_VIDEO = "deleteCommentVideo";
    public static final String DELETE_COMMENT_BOARD = "deleteCommentBoard";
    public static final String DELETE_COMMENT_MARKET = "deleteCommentMarket";
    public static final String DELETE_COMMENT_DISCUSSION = "deleteCommentDiscussion";
    public static final String DELETE_COMMENT_CHANNEL_COMMENT = "deleteCommentChannelComment";
    public static final String RESTORE_COMMENT = "restoreComment";
    public static final String BAN_USER = "banUser";
    public static final String REPLY_TO_COMMENT = "replyToComment";
    public static final String NOTIFY_AUTHOR = "notifyAuthor";

    private ModerationFeatureFlags() {}
}
