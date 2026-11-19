package ru.posts.social.inbound;

/** Normalized inbound social event kinds (BE-32). */
public enum SocialEventKind {
    POST_NEW,
    POST_SUGGESTED,
    POST_EDITED,
    POST_REMOVED,
    COMMENT_NEW,
    COMMENT_EDITED,
    COMMENT_REMOVED,
    MESSAGE_NEW,
    MEMBER_JOINED,
    BOT_MEMBERSHIP_CHANGED
}
