package ru.posts.antispam.model;

import java.util.Set;

/** Runtime context that cannot be derived from the event alone. */
public record EvaluationContext(
        boolean authorIsAdmin,
        boolean authorIsMember,
        boolean underAds,
        boolean underPinned,
        boolean isAutomaticForward,
        boolean fromSenderChat,
        boolean ownCommunityOrChannel,
        boolean repeatHit,
        String communityOrChannelId,
        Set<String> attachmentTypes) {

    public static EvaluationContext empty() {
        return new EvaluationContext(false, true, false, false, false, false, false, false, null, Set.of());
    }
}
