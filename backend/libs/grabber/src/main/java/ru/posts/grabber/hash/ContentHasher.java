package ru.posts.grabber.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** SHA-256 content hash per grabber.md §5.2. */
public final class ContentHasher {

    private ContentHasher() {}

    public static String hash(String text, Collection<String> mediaSha256, Collection<String> attachmentCanonicalIds) {
        String normalized = normalizeWs(text);
        List<String> media = sorted(mediaSha256);
        List<String> attachments = sorted(attachmentCanonicalIds);
        String payload = normalized + "\n" + String.join(",", media) + "\n" + String.join(",", attachments);
        return sha256Hex(payload);
    }

    public static String normalizeWs(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
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

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
