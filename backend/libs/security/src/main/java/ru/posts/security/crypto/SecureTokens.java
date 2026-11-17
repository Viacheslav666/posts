package ru.posts.security.crypto;

import java.security.SecureRandom;
import java.util.Base64;

/** Cryptographically strong opaque token generation. */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {}

    public static String urlSafe(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
