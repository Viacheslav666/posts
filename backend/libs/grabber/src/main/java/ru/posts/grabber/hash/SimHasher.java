package ru.posts.grabber.hash;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * 64-bit SimHash over character shingles for near-duplicate detection.
 *
 * <p>Similarity = 1 - hamming(a,b)/64. Default grabber threshold is 0.80 (≥80% similar → suppress).
 */
public final class SimHasher {

    private static final int SHINGLE_SIZE = 3;

    private SimHasher() {}

    public static long fingerprint(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return 0L;
        }
        int[] bits = new int[64];
        if (normalized.length() < SHINGLE_SIZE) {
            applyHash(bits, hashToken(normalized));
        } else {
            for (int i = 0; i <= normalized.length() - SHINGLE_SIZE; i++) {
                applyHash(bits, hashToken(normalized.substring(i, i + SHINGLE_SIZE)));
            }
        }
        long out = 0L;
        for (int i = 0; i < 64; i++) {
            if (bits[i] > 0) {
                out |= 1L << i;
            }
        }
        return out;
    }

    public static int hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    /** Similarity in [0,1]. */
    public static double similarity(long a, long b) {
        return 1.0d - (hammingDistance(a, b) / 64.0d);
    }

    public static boolean similarEnough(long a, long b, double threshold) {
        if (threshold <= 0) {
            return false;
        }
        if (threshold > 1) {
            threshold = 1;
        }
        return similarity(a, b) + 1e-9 >= threshold;
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); ) {
            int cp = lower.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetterOrDigit(cp)) {
                sb.appendCodePoint(cp);
            } else if (Character.isWhitespace(cp) || isPunctuation(cp)) {
                if (sb.isEmpty() || sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
            }
        }
        return sb.toString().trim();
    }

    private static boolean isPunctuation(int cp) {
        int type = Character.getType(cp);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    private static void applyHash(int[] bits, long hash) {
        for (int i = 0; i < 64; i++) {
            if (((hash >>> i) & 1L) == 1L) {
                bits[i]++;
            } else {
                bits[i]--;
            }
        }
    }

    private static long hashToken(String token) {
        CRC32 crc = new CRC32();
        crc.update(token.getBytes(StandardCharsets.UTF_8));
        long low = crc.getValue();
        // mix a second CRC over reversed bytes for upper 32 bits
        CRC32 crc2 = new CRC32();
        byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
        for (int i = bytes.length - 1; i >= 0; i--) {
            crc2.update(bytes[i]);
        }
        long high = crc2.getValue();
        return (high << 32) ^ low ^ (token.hashCode() * 0x9E3779B97F4A7C15L);
    }
}
