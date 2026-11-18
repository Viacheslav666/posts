package ru.posts.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates VK string attachment tokens used by the editor / import path, e.g. {@code photo-1_2},
 * {@code video-123_456}, {@code doc1_2_accesskey}.
 */
public final class VkStringAttachmentValidator {

    /**
     * VK attachment: {@code type}{@code ownerId}_{@code mediaId}[_{@code accessKey}].
     *
     * <p>Types commonly used in wall posts / vposter string attachments.
     */
    private static final Pattern ATTACHMENT = Pattern.compile(
            "^(photo|video|audio|doc|poll|market|album|page|note|clip|audio_playlist|market_album)"
                    + "-?\\d+_\\d+(?:_[A-Za-z0-9_-]+)?$",
            Pattern.CASE_INSENSITIVE);

    private VkStringAttachmentValidator() {}

    public static boolean isValid(String attachment) {
        if (attachment == null || attachment.isBlank()) {
            return false;
        }
        return ATTACHMENT.matcher(attachment.trim()).matches();
    }

    /** Returns invalid tokens (empty when all ok). */
    public static List<String> invalidOf(List<String> attachments) {
        List<String> invalid = new ArrayList<>();
        if (attachments == null) {
            return invalid;
        }
        for (String raw : attachments) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String token = raw.trim();
            if (!isValid(token)) {
                invalid.add(token);
            }
        }
        return List.copyOf(invalid);
    }

    public static List<String> normalize(List<String> attachments) {
        List<String> out = new ArrayList<>();
        if (attachments == null) {
            return List.of();
        }
        for (String raw : attachments) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String token = raw.trim();
            // Preserve VK casing for type prefix commonly lowercase.
            int us = token.indexOf('_');
            if (us > 0) {
                String typeAndOwner = token.substring(0, us);
                int digit = firstDigit(typeAndOwner);
                if (digit > 0) {
                    String type = typeAndOwner.substring(0, digit).toLowerCase(Locale.ROOT);
                    token = type + typeAndOwner.substring(digit) + token.substring(us);
                }
            }
            out.add(token);
        }
        return List.copyOf(out);
    }

    private static int firstDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '-' || (c >= '0' && c <= '9')) {
                return i;
            }
        }
        return -1;
    }
}
