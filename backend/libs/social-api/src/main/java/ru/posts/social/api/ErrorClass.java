package ru.posts.social.api;

/** Classification of social API failures for publication retries (ADR-0004). */
public enum ErrorClass {
    /** Transient: 5xx, timeouts, network blips — retry via next_attempt_at. */
    RETRYABLE,
    /** Rate / flood control — slow the Bucket4j key and retry after wait. */
    RATE_LIMITED,
    /** Token revoked / reauth required. */
    AUTH,
    /** Permanent content / permission rejection — no retry. */
    PERMANENT,
    /** Call succeeded but response was lost — resolve via findExisting. */
    UNKNOWN_OUTCOME
}
