package ru.posts.social.api;

import java.util.Optional;

/**
 * Port for a single social network adapter (02-backend.md §6). Defaults keep existing Telegram +
 * publisher code compiling while VK/OK/Max add richer operations.
 */
public interface SocialAdapter {

    Network network();

    default NetworkCapabilities capabilities() {
        return NetworkCapabilities.minimal(network());
    }

    PublishResult publish(ChannelContext ctx, PostVariantContent content, String idempotencyKey);

    Optional<PublishResult> findExisting(ChannelContext ctx, String idempotencyKey, PostVariantContent content);

    ErrorClass classifyError(Throwable error);

    default RemoteMediaRef uploadMedia(ChannelContext ctx, MediaAssetRef asset) {
        throw new SocialApiException(
                ErrorClass.PERMANENT,
                "uploadMedia is not supported for network " + network().code());
    }

    default void delete(ChannelContext ctx, String remotePostId) {
        // no-op by default
    }

    default Optional<TokenSet> refreshToken(TokenRefreshRequest request) {
        return Optional.empty();
    }
}
