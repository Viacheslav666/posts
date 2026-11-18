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
    public static final String PROMO_INVALID = "promo_invalid";
    public static final String PAYMENT_PROVIDER_ERROR = "payment_provider_error";
    public static final String BILLING_FROZEN = "billing_frozen";
    public static final String BILLING_INSUFFICIENT_FUNDS = "billing_insufficient_funds";
    public static final String BILLING_REQUISITES_INVALID = "billing_requisites_invalid";
    public static final String EMAIL_NOT_VERIFIED = "email_not_verified";
    public static final String CHANNEL_REAUTH_REQUIRED = "channel_reauth_required";
    public static final String OPTIMISTIC_LOCK_CONFLICT = "optimistic_lock_conflict";
    public static final String TOTP_REQUIRED = "totp_required";
    public static final String OAUTH_LINK_REQUIRED = "oauth_link_required";
    public static final String INVALID_TOKEN = "invalid_token";
    public static final String INGEST_RESPONSIBILITY_REQUIRED = "ingest_responsibility_required";
    public static final String INGEST_ATTRIBUTION_REQUIRED = "ingest_attribution_required";
    public static final String INGEST_SOURCE_UNREACHABLE = "ingest_source_unreachable";
    public static final String INGEST_SSRF_BLOCKED = "ingest_ssrf_blocked";
    public static final String INGEST_RATE_LIMITED = "ingest_rate_limited";
    public static final String INGEST_AUTO_PAUSED = "ingest_auto_paused";
    public static final String INGEST_VALIDATION_FAILED = "ingest_validation_failed";
    public static final String INGEST_DEDUP_CONFLICT = "ingest_dedup_conflict";

    public static final String ORD_NOT_CONFIGURED = "ord_not_configured";
    public static final String ORD_VALIDATION_FAILED = "ord_validation_failed";
    public static final String ORD_PROVIDER_UNAVAILABLE = "ord_provider_unavailable";
    public static final String ORD_ERID_MISSING = "ord_erid_missing";
    public static final String AD_INN_INVALID = "ad_inn_invalid";
    public static final String NETWORK_OPTION_UNSUPPORTED = "network_option_unsupported";

    public static final String STORY_CONNECTION_REQUIRED = "story_connection_required";
    public static final String STORY_UNSUPPORTED_NETWORK = "story_unsupported_network";
    public static final String STORY_MEDIA_INVALID = "story_media_invalid";
    public static final String STORY_LINK_INVALID = "story_link_invalid";
    public static final String STORY_TTL_UNSUPPORTED = "story_ttl_unsupported";
    public static final String STORY_BATCH_LIMIT = "story_batch_limit";

    public static final String PLATFORM_ADMIN_REQUIRED = "platform_admin_required";
    public static final String IMPERSONATION_FORBIDDEN = "impersonation_forbidden";

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
            - promo_invalid — promo code inactive, expired, or not applicable
            - payment_provider_error — YooKassa / payment provider failure
            - billing_frozen — workspace frozen for non-payment; mutations blocked
            - billing_insufficient_funds — wallet balance too low for daily charge / operation
            - billing_requisites_invalid — INN/KPP/OGRN validation failed for legal invoice
            - email_not_verified — action requires verified email
            - channel_reauth_required — channel token revoked/expired
            - totp_required — password ok, TOTP/backup code required (401)
            - oauth_link_required — OAuth email matches existing account; confirm link (409)
            - invalid_token — email verify / password reset / OAuth link token invalid
            - ord_not_configured — workspace has no active ORD connection
            - ord_validation_failed — ORD request rejected by validation
            - ord_provider_unavailable — ORD provider unreachable / 5xx
            - ord_erid_missing — creative registered without erid
            - ad_inn_invalid — advertiser INN must be 10 or 12 digits
            - network_option_unsupported — option not supported for selected network
            - story_connection_required — VK channel lacks stories scope connection
            - story_unsupported_network — stories only supported for vk/ok (not tg/max/ig)
            - story_media_invalid — story requires exactly one valid photo/video media
            - story_link_invalid — VK story CTA URL must be vk.com / vk.ru
            - story_ttl_unsupported — invalid stories_time / TTL enum
            - story_batch_limit — batch exceeds posts.stories.batch_max (default 50)
            - platform_admin_required — /admin endpoints require users.platform_admin
            - impersonation_forbidden — dangerous action blocked while impersonating
            - ingest_responsibility_required — grabber activate without legal checkbox
            - ingest_attribution_required — grabber activate without attribution
            - ingest_ssrf_blocked — RSS/website URL targets private network
            - ingest_rate_limited — manual grabber run too frequent
            - ingest_source_unreachable — source fetch failed
            - ingest_auto_paused — rule auto-paused after errors
            - ingest_validation_failed — item failed network capability checks
            - ingest_dedup_conflict — duplicate content detected
            """;

    private ProblemCodes() {}
}
