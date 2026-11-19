package ru.posts.emulator.control;

public enum ScenarioFault {
    NONE,
    /** VK User authorization failed — maps to AUTH / waiting_for_channel for stories. */
    VK_5,
    VK_6,
    VK_9,
    VK_10,
    VK_15,
    /** Group authorization failed / group auth disabled. */
    VK_27,
    /** Access to wall's posts denied. */
    VK_203,
    VK_100,
    HTTP_5XX,
    HTTP_429,
    /** Permanent client/auth failure (401/403/400) — no retries expected. */
    HTTP_FORBIDDEN,
    /** Max Bot API access.denied (permission). */
    ACCESS_DENIED,
    DROP,
    HANG;

    public static ScenarioFault from(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        String value = raw.trim().toLowerCase();
        return switch (value) {
            case "5", "vk_5", "user_auth", "authorization_failed" -> VK_5;
            case "6", "vk_6", "too_many_requests" -> VK_6;
            case "9", "vk_9", "flood" -> VK_9;
            case "10", "vk_10", "internal" -> VK_10;
            case "15", "vk_15" -> VK_15;
            case "27", "vk_27", "group_auth" -> VK_27;
            case "203", "vk_203", "wall_access" -> VK_203;
            case "100", "vk_100", "param" -> VK_100;
            case "access.denied", "access_denied", "max_access_denied" -> ACCESS_DENIED;
            case "5xx", "500", "502", "503", "http_5xx" -> HTTP_5XX;
            case "429", "rate_limit", "http_429" -> HTTP_429;
            case "403", "401", "400", "permanent", "forbidden", "no_rights", "auth" -> HTTP_FORBIDDEN;
            case "drop", "success_without_response", "lost_response" -> DROP;
            case "hang", "timeout", "no_response" -> HANG;
            default -> throw new IllegalArgumentException("Unknown forcedError: " + raw);
        };
    }
}
