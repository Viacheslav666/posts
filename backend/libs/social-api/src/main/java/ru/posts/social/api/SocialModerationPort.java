package ru.posts.social.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Separate SPI for remote moderation / suggest actions (BE-33). Does not extend {@link
 * SocialAdapter} — adapters may implement both independently.
 */
public interface SocialModerationPort {

    Network network();

    /** Features for moderation flags ({@link ModerationFeatureFlags}). */
    NetworkCapabilities moderationCapabilities();

    ModerationResult publishSuggested(ChannelContext ctx, ModerationTarget target, ModerationPayload payload);

    ModerationResult deletePost(ChannelContext ctx, ModerationTarget target);

    ModerationResult deleteComment(ChannelContext ctx, ModerationTarget target);

    ModerationResult restoreComment(ChannelContext ctx, ModerationTarget target);

    ModerationResult banUser(ChannelContext ctx, ModerationTarget target, ModerationPayload payload);

    ModerationResult unbanUser(ChannelContext ctx, ModerationTarget target);

    ModerationResult replyToComment(ChannelContext ctx, ModerationTarget target, ModerationPayload payload);

    ModerationResult notifyAuthor(ChannelContext ctx, ModerationTarget target, ModerationPayload payload);

    /**
     * Batch via VK {@code execute} when queue &gt; 10; default executes sequentially.
     *
     * <p>Kinds: {@code publish_suggested}, {@code delete_post}, {@code delete_comment}, {@code
     * restore_comment}, {@code ban_user}, {@code unban_user}, {@code reply_comment}, {@code
     * notify_author}.
     */
    default List<ModerationBatchItemResult> executeBatch(ChannelContext ctx, List<ModerationBatchItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<ModerationBatchItemResult> out = new ArrayList<>(items.size());
        for (ModerationBatchItem item : items) {
            out.add(executeOne(ctx, item));
        }
        return List.copyOf(out);
    }

    private ModerationBatchItemResult executeOne(ChannelContext ctx, ModerationBatchItem item) {
        try {
            ModerationPayload payload = item.payload() == null ? ModerationPayload.empty() : item.payload();
            ModerationTarget target = item.target();
            ModerationResult result =
                    switch (normalizeKind(item.kind())) {
                        case "publish_suggested" -> publishSuggested(ctx, target, payload);
                        case "delete_post" -> deletePost(ctx, target);
                        case "delete_comment" -> deleteComment(ctx, target);
                        case "restore_comment" -> restoreComment(ctx, target);
                        case "ban_user" -> banUser(ctx, target, payload);
                        case "unban_user" -> unbanUser(ctx, target);
                        case "reply_comment" -> replyToComment(ctx, target, payload);
                        case "notify_author" -> notifyAuthor(ctx, target, payload);
                        default -> throw new SocialApiException(
                                ErrorClass.PERMANENT, "unknown moderation kind: " + item.kind());
                    };
            return ModerationBatchItemResult.ok(item.actionId(), result);
        } catch (SocialApiException ex) {
            return ModerationBatchItemResult.fail(item.actionId(), ex.errorClass(), ex.getMessage());
        } catch (RuntimeException ex) {
            ErrorClass classified = classifyError(ex);
            return ModerationBatchItemResult.fail(item.actionId(), classified, ex.getMessage());
        }
    }

    private static String normalizeKind(String kind) {
        return kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
    }

    Optional<ModerationResult> reconcilePublishSuggested(
            ChannelContext ctx, ModerationTarget target, String idempotencyKey);

    ErrorClass classifyError(Throwable error);
}
