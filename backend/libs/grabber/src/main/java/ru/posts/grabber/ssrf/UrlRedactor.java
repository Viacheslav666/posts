package ru.posts.grabber.ssrf;

import java.util.regex.Pattern;

/** Drops query strings so signed CDN tokens never land in logs or stored error text. */
public final class UrlRedactor {

    private static final Pattern WITH_QUERY = Pattern.compile("(https?://[^\\s?]+)\\?\\S*");

    private UrlRedactor() {}

    public static String stripQuery(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return WITH_QUERY.matcher(value).replaceAll("$1");
    }
}
