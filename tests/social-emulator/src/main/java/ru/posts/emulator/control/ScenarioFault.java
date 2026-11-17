package ru.posts.emulator.control;

public enum ScenarioFault {
    NONE,
    VK_6,
    VK_9,
    VK_10,
    VK_15,
    VK_100,
    HTTP_5XX,
    HTTP_429,
    DROP,
    HANG;

    public static ScenarioFault from(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        String value = raw.trim().toLowerCase();
        return switch (value) {
            case "6", "vk_6", "too_many_requests" -> VK_6;
            case "9", "vk_9", "flood" -> VK_9;
            case "10", "vk_10", "internal" -> VK_10;
            case "15", "vk_15", "access_denied" -> VK_15;
            case "100", "vk_100", "param" -> VK_100;
            case "5xx", "500", "502", "503", "http_5xx" -> HTTP_5XX;
            case "429", "rate_limit", "http_429" -> HTTP_429;
            case "drop", "success_without_response", "lost_response" -> DROP;
            case "hang", "timeout", "no_response" -> HANG;
            default -> throw new IllegalArgumentException("Unknown forcedError: " + raw);
        };
    }
}
