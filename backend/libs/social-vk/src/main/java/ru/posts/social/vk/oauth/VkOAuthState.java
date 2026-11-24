package ru.posts.social.vk.oauth;

import java.security.SecureRandom;
import java.util.Base64;

/** URL-safe OAuth {@code state} of 32 characters ({@code [A-Za-z0-9_-]}). */
public final class VkOAuthState {

    private static final SecureRandom RANDOM = new SecureRandom();

    private VkOAuthState() {}

    public static String random() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
