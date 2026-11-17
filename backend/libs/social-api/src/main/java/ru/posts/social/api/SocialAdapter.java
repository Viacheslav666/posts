package ru.posts.social.api;

import java.util.Optional;

/**
 * Port for a single social network adapter (02-backend.md §6). Prototype implements Telegram against
 * the social emulator.
 */
public interface SocialAdapter {

    Network network();

    PublishResult publish(ChannelContext ctx, PostVariantContent content, String idempotencyKey);

    Optional<PublishResult> findExisting(ChannelContext ctx, String idempotencyKey, PostVariantContent content);

    ErrorClass classifyError(Throwable error);
}
