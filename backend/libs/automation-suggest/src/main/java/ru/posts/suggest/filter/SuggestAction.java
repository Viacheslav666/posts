package ru.posts.suggest.filter;

/**
 * Filter verdict. Mode handling ({@code auto}/{@code manual}/{@code hybrid}) is outside the engine —
 * the engine only returns {@link #PUBLISH}, {@link #REJECT}, or {@link #FILTER_OUT}. {@link #MODERATE}
 * is reserved for callers that map hybrid edge-cases.
 */
public enum SuggestAction {
    /** Passed filters — eligible for auto-publish / slotting. */
    PUBLISH,
    /** Soft deferral (limits, schedule, dedup) — keep item, do not hard-delete by reject policy. */
    FILTER_OUT,
    /** Hard fail — apply on_reject policy (delete / notify). */
    REJECT,
    /** Reserved for hybrid/manual routing outside the pure filter engine. */
    MODERATE
}
