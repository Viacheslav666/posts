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

    /** Text half of a fingerprint ({@code sim:hex}), ignoring the media digest. */
    public static String simKey(String fingerprint) {
        if (fingerprint == null) {
            return null;
        }
        int start = fingerprint.indexOf("sim:");
        if (start < 0) {
            return null;
        }
        int end = fingerprint.indexOf('|', start);
        return end < 0 ? fingerprint.substring(start) : fingerprint.substring(start, end);
    }
}
