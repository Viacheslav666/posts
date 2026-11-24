package ru.posts.social.api;

import java.time.Instant;
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

    /**
     * BE-16 extension: publish a story. Default throws permanent — networks without stories must
     * reject explicitly.
     */
    default PublishResult publishStory(ChannelContext ctx, PostVariantContent content, String idempotencyKey) {
        throw new SocialApiException(
                ErrorClass.PERMANENT,
                "stories are not supported for network " + network().code());
    }

    Optional<PublishResult> findExisting(ChannelContext ctx, String idempotencyKey, PostVariantContent content);

    default Optional<PublishResult> findExistingStory(
            ChannelContext ctx, String idempotencyKey, PostVariantContent content) {
        return findExisting(ctx, idempotencyKey, content);
    }

    ErrorClass classifyError(Throwable error);

    default RemoteMediaRef uploadMedia(ChannelContext ctx, MediaAssetRef asset) {
        throw new SocialApiException(
                ErrorClass.PERMANENT,
                "uploadMedia is not supported for network " + network().code());
    }

    default void delete(ChannelContext ctx, String remotePostId) {
        // no-op by default — already-deleted is success for scheduled_actions
    }

    default void deleteStory(ChannelContext ctx, String remoteStoryId) {
        delete(ctx, remoteStoryId);
    }

    default void pin(ChannelContext ctx, String remotePostId, boolean pinned) {
        // no-op by default
    }

    default void comment(ChannelContext ctx, String remotePostId, String text) {
        // no-op by default
    }

    default void comment(ChannelContext ctx, String remotePostId, String text, java.util.List<String> attachments) {
        comment(ctx, remotePostId, text);
    }

    /**
     * {@code guid} is the scheduled-action id. VK uses it so a retry of wall.createComment does not
     * add a second comment. Null omits the parameter.
     */
    default void comment(
            ChannelContext ctx, String remotePostId, String text, java.util.List<String> attachments, String guid) {
        comment(ctx, remotePostId, text, attachments);
    }

    default Optional<TokenSet> refreshToken(TokenRefreshRequest request) {
        return Optional.empty();
    }

    /**
     * BE-26: fetch impression / view count for a published post. Default returns empty — networks
     * without a stats API skip ORD views_source=unavailable.
     */
    default Optional<Long> fetchImpressions(ChannelContext ctx, String remotePostId) {
        return Optional.empty();
    }

    /** BE-26: story view count when the network exposes it. */
    default Optional<Long> fetchStoryImpressions(ChannelContext ctx, String remoteStoryId) {
        return fetchImpressions(ctx, remoteStoryId);
    }

    /**
     * BE-29: resolve today's birthday members for {@code {birthdays}} / {@code {именинники}}.
     * Default empty. VK uses {@code groups.getMembers} with {@code bdate}; missing rights → permanent
     * error with a clear message.
     */
    default String resolveBirthdays(ChannelContext ctx, Instant at, String timezone) {
        return "";
    }
}
