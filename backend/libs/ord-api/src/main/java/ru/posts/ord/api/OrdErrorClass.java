package ru.posts.ord.api;

/** Classification of ORD API failures for registration / reporting retries. */
public enum OrdErrorClass {
    /** Transient: 5xx, timeouts, network blips. */
    RETRYABLE,
    /** Rate / flood control — slow down and retry after wait. */
    RATE_LIMITED,
    /** API key revoked / reauth required. */
    AUTH,
    /** Permanent rejection — do not retry the same payload. */
    PERMANENT,
    /** Request failed schema / business validation. */
    VALIDATION
}
