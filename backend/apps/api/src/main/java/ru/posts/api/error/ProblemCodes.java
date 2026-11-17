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
    public static final String INSUFFICIENT_PERMISSIONS = "insufficient_permissions";
    public static final String TRIAL_ALREADY_USED = "trial_already_used";
    public static final String EMAIL_NOT_VERIFIED = "email_not_verified";
    public static final String CHANNEL_REAUTH_REQUIRED = "channel_reauth_required";
    public static final String OPTIMISTIC_LOCK_CONFLICT = "optimistic_lock_conflict";
    public static final String TOTP_REQUIRED = "totp_required";
    public static final String OAUTH_LINK_REQUIRED = "oauth_link_required";
    public static final String INVALID_TOKEN = "invalid_token";

    /** Catalog for OpenAPI `info.description` / FE branching. */
    public static final String OPENAPI_CATALOG =
            """
            Machine-readable ProblemDetail.code values:
            - not_implemented — stub endpoint (BE-3)
            - validation_failed — Bean Validation / malformed request
            - not_found — resource missing or IDOR (foreign workspace → 404)
            - conflict — state conflict
            - optimistic_lock_conflict — post.version mismatch (409)
            - unauthorized — missing/invalid access token (401)
            - forbidden — authenticated but lacking permission (403)
            - plan_limit_exceeded — tariff limit (402)
            - rate_limited — API or login throttle (429)
            - idempotency_conflict — Idempotency-Key reuse with different body
            - insufficient_permissions — channel/bot lacks required rights
            - trial_already_used — Pro trial already consumed for the account
            - email_not_verified — action requires verified email
            - channel_reauth_required — channel token revoked/expired
            - totp_required — password ok, TOTP/backup code required (401)
            - oauth_link_required — OAuth email matches existing account; confirm link (409)
            - invalid_token — email verify / password reset / OAuth link token invalid
            """;

    private ProblemCodes() {}
}
