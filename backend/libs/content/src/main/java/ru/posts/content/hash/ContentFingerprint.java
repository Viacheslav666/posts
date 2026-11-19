package ru.posts.content.hash;

import java.util.Collection;

/**
 * Combined text SimHash + media digest fingerprint for own-post recognition (BE-33).
 *
 * <p>Format: {@code sim:{hex64}|media:{sha256}} where {@code hex64} is the zero-padded 16-digit hex
 * of the 64-bit SimHash, and {@code sha256} is a ContentHasher-style digest of media SHA-256s.
 */
public final class ContentFingerprint {

    private ContentFingerprint() {}

    public static String fingerprint(String text, Collection<String> mediaSha256) {
        long sim = SimHasher.fingerprint(text);
        String simHex = String.format("%016x", sim);
        String mediaHex = ContentHasher.mediaDigest(mediaSha256);
        return "sim:" + simHex + "|media:" + mediaHex;
    }
}
