package ru.posts.notifications.channel;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/**
 * Parses {@code POSTS_MAIL_FROM} as an address with an optional display name.
 *
 * <p>When the value already includes a display name, callers must not wrap it again with {@code
 * setFrom(address, personal)} or the header is nested twice.
 */
public final class MailFromAddress {

    public record Parsed(String address, String personal) {}

    private MailFromAddress() {}

    public static Parsed parse(String raw, String fallbackName) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("mail from is blank");
        }
        try {
            InternetAddress[] parsed = InternetAddress.parse(value, false);
            if (parsed.length == 0
                    || parsed[0].getAddress() == null
                    || parsed[0].getAddress().isBlank()) {
                return new Parsed(value, blankToNull(fallbackName));
            }
            InternetAddress address = parsed[0];
            String personal = address.getPersonal();
            if (personal == null || personal.isBlank()) {
                personal = blankToNull(fallbackName);
            }
            return new Parsed(address.getAddress().trim(), personal);
        } catch (AddressException ex) {
            return new Parsed(value, blankToNull(fallbackName));
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
