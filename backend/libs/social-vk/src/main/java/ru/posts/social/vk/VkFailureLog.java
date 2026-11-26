package ru.posts.social.vk;

import org.slf4j.Logger;

/** Logs VK method failures without access tokens. */
public final class VkFailureLog {

    private VkFailureLog() {}

    public static void warn(Logger log, String method, Object errorCode, String errorMessage) {
        if (log == null) {
            return;
        }
        log.warn(
                "VK API failure method={} error_code={} error_msg={}",
                method == null ? "vk" : method,
                errorCode,
                redact(errorMessage));
    }

    /** Strips VK ID / classic access tokens and {@code access_token} query values. */
    public static String redact(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String redacted = message.replaceAll("vk[12]\\.[A-Za-z0-9._\\-]+", "[redacted]");
        return redacted.replaceAll("(?i)access_token=[^&\\s\"']+", "access_token=[redacted]");
    }
}
