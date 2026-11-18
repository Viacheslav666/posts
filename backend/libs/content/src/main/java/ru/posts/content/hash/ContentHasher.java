package ru.posts.content.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Shared content-hash helpers for fingerprinting (BE-33).
 *
 * <p>Subset of {@code ru.posts.grabber.hash.ContentHasher} so moderation / publisher do not depend
 * on grabber.
 */
public final class ContentHasher {

    private ContentHasher() {}

    public static String normalizeWs(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /** Sorted, lower-cased non-blank media digests joined with comma, then SHA-256. */
    public static String mediaDigest(Collection<String> mediaSha256) {
        return sha256Hex(String.join(",", sorted(mediaSha256)));
    }

    private static List<String> sorted(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> copy = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                copy.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        Collections.sort(copy);
        return copy;
    }
}
