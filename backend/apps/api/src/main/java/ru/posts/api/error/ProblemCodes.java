package ru.posts.api.error;

/** Stable machine-readable problem codes exposed in RFC 7807 {@code code} property. */
public final class ProblemCodes {

    public static final String NOT_IMPLEMENTED = "not_implemented";
    public static final String VALIDATION_FAILED = "validation_failed";
    public static final String NOT_FOUND = "not_found";
    public static final String CONFLICT = "conflict";
    public static final String UNAUTHORIZED = "unauthorized";
    public static final String FORBIDDEN = "forbidden";
    public static final String PLAN_LIMIT_EXCEEDED = "plan_limit_exceeded";
    public static final String RATE_LIMITED = "rate_limited";
    public static final String IDEMPOTENCY_CONFLICT = "idempotency_conflict";

    private ProblemCodes() {}
}
