package ru.posts.social.api;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Hard guard for social-network integration tests (QA-14 / P2).
 *
 * <p>Tests and CI must never call production social API hosts ({@code api.vk.com}, {@code
 * api.telegram.org}, {@code platform-api2.max.ru}, {@code api.ok.ru}). Use {@code
 * tests/social-emulator} or WireMock instead.
 */
public final class SocialLiveApiGuard {

    public static final String VK_PRODUCTION_HOST = "api.vk.com";
    public static final String TELEGRAM_PRODUCTION_HOST = "api.telegram.org";
    public static final String MAX_PRODUCTION_HOST = "platform-api2.max.ru";
    public static final String OK_PRODUCTION_HOST = "api.ok.ru";

    private static final List<String> FORBIDDEN_HOSTS =
            List.of(VK_PRODUCTION_HOST, TELEGRAM_PRODUCTION_HOST, MAX_PRODUCTION_HOST, OK_PRODUCTION_HOST);

    private SocialLiveApiGuard() {}

    /**
     * Fails fast when {@code url} targets a forbidden production host.
     *
     * @throws AssertionError on production or unparseable host
     */
    public static void assertNotProductionSocialUrl(String url) {
        String host = hostOf(url);
        if (host.isEmpty()) {
            throw new AssertionError("Social URL must not be blank");
        }
        for (String forbidden : FORBIDDEN_HOSTS) {
            if (host.equals(forbidden) || host.endsWith("." + forbidden)) {
                throw new AssertionError("Refusing production social API host " + forbidden + " in tests: " + url);
            }
        }
    }

    /** Returns {@code true} when {@code url} resolves to a forbidden production social host. */
    public static boolean isForbiddenProductionHost(String url) {
        try {
            String host = hostOf(url);
            if (host.isEmpty()) {
                return false;
            }
            for (String forbidden : FORBIDDEN_HOSTS) {
                if (host.equals(forbidden) || host.endsWith("." + forbidden)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            return true;
        }
    }

    private static String hostOf(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            URI uri = URI.create(trimmed.contains("://") ? trimmed : "https://" + trimmed);
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            String lower = trimmed.toLowerCase(Locale.ROOT);
            for (String forbidden : FORBIDDEN_HOSTS) {
                if (lower.contains(forbidden)) {
                    return forbidden;
                }
            }
            return lower;
        }
    }
}
