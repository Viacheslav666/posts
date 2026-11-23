package ru.posts.grabber.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Normalizes grabber source ids (spaces, @screen_name, club/public prefixes). */
public final class SourceIdNormalizer {

    private static final Pattern NUMERIC = Pattern.compile("^-?\\d+$");
    private static final Pattern SCREEN = Pattern.compile("^@?[A-Za-z0-9._]+$");

    private SourceIdNormalizer() {}

    public static List<String> normalizeAll(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String token : raw) {
            if (token == null) {
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                if (c == ',' || c == ';' || Character.isWhitespace(c)) {
                    addNormalized(out, current);
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
            addNormalized(out, current);
        }
        return List.copyOf(out);
    }

    private static void addNormalized(Set<String> out, StringBuilder current) {
        if (current.isEmpty()) {
            return;
        }
        String normalized = normalizeOne(current.toString());
        if (normalized != null) {
            out.add(normalized);
        }
    }

    public static String normalizeOne(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        value = stripVkUrl(value);
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("club") && NUMERIC.matcher(lower.substring(4)).matches()) {
            return "-" + lower.substring(4);
        }
        if (lower.startsWith("public") && NUMERIC.matcher(lower.substring(6)).matches()) {
            return "-" + lower.substring(6);
        }
        if (NUMERIC.matcher(value).matches() || SCREEN.matcher(value).matches()) {
            return value;
        }
        throw new IllegalArgumentException("Invalid source id: " + raw);
    }

    /** Strips {@code https://vk.com/} and {@code vk.ru} so {@code club123} / {@code public123} remain. */
    static String stripVkUrl(String value) {
        String v = value;
        if (v.regionMatches(true, 0, "https://", 0, 8)) {
            v = v.substring(8);
        } else if (v.regionMatches(true, 0, "http://", 0, 7)) {
            v = v.substring(7);
        }
        if (v.regionMatches(true, 0, "www.", 0, 4)) {
            v = v.substring(4);
        }
        if (v.regionMatches(true, 0, "vk.com/", 0, 7)) {
            v = v.substring(7);
        } else if (v.regionMatches(true, 0, "vk.ru/", 0, 6)) {
            v = v.substring(6);
        }
        int cut = v.length();
        int q = v.indexOf('?');
        if (q >= 0) {
            cut = Math.min(cut, q);
        }
        int hash = v.indexOf('#');
        if (hash >= 0) {
            cut = Math.min(cut, hash);
        }
        v = v.substring(0, cut);
        if (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    public static List<String> requireValid(List<String> raw, int max) {
        List<String> normalized = normalizeAll(raw);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one source id is required");
        }
        if (normalized.size() > max) {
            throw new IllegalArgumentException("Too many source ids (max " + max + ")");
        }
        return new ArrayList<>(normalized);
    }
}
