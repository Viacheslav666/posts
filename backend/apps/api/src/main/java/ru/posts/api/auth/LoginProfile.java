package ru.posts.api.auth;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Derives {@code GET /me} login method and a social avatar URL from session and identity data.
 */
public final class LoginProfile {

    public static final String EMAIL = "email";
    public static final String VK = "vk";
    public static final String YANDEX = "yandex";
    public static final String TELEGRAM = "telegram";

    private static final Set<String> METHODS = Set.of(EMAIL, VK, YANDEX, TELEGRAM);
    private static final List<String> AVATAR_KEYS =
            List.of("avatar", "photo_200", "photo_max_orig", "photo_max", "photo_100", "photo");

    private LoginProfile() {}

    /**
     * Session provider wins. Otherwise the earliest linked identity (how the account was created). Otherwise email
     * when a password exists.
     */
    public static String loginMethod(String sessionProvider, List<String> identityProviders, boolean hasPassword) {
        String session = normalize(sessionProvider);
        if (session != null) {
            return session;
        }
        if (identityProviders != null) {
            for (String provider : identityProviders) {
                String normalized = normalize(provider);
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        if (hasPassword) {
            return EMAIL;
        }
        return null;
    }

    /** Photo URL from VK ID / Yandex user info already stored on the identity profile. */
    public static String avatarUrl(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        for (String key : AVATAR_KEYS) {
            Object value = raw.get(key);
            if (value instanceof String text) {
                String trimmed = text.trim();
                if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    private static String normalize(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        String value = provider.trim().toLowerCase(Locale.ROOT);
        return METHODS.contains(value) ? value : null;
    }
}
