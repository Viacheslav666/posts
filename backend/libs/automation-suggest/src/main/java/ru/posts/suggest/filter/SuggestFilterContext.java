package ru.posts.suggest.filter;

import java.time.Instant;
import java.util.List;

/**
 * Runtime context for filter evaluation (author profile + counters + recent simhashes).
 *
 * @param mode auto|manual|hybrid
 */
public record SuggestFilterContext(
        String mode,
        Boolean authorMessagesAllowed,
        Boolean authorClosedProfile,
        List<Long> recentSimhashes,
        AuthorCounts authorPublishCounts,
        AuthorCounts globalPublishCounts,
        boolean lastWallWasAds,
        Instant now) {

    public SuggestFilterContext {
        recentSimhashes = recentSimhashes == null ? List.of() : List.copyOf(recentSimhashes);
        mode = mode == null || mode.isBlank() ? "auto" : mode;
        now = now == null ? Instant.now() : now;
        authorPublishCounts = authorPublishCounts == null ? AuthorCounts.ZERO : authorPublishCounts;
        globalPublishCounts = globalPublishCounts == null ? AuthorCounts.ZERO : globalPublishCounts;
    }

    public record AuthorCounts(long hour, long day, long week) {
        public static final AuthorCounts ZERO = new AuthorCounts(0, 0, 0);
    }
}
