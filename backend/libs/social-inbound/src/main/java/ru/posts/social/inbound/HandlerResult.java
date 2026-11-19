package ru.posts.social.inbound;

/** Result of a {@link SocialEventHandler}. {@code CONSUMED} stops the chain. */
public enum HandlerResult {
    CONTINUE,
    CONSUMED
}
