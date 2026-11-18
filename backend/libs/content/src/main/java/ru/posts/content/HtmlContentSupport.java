package ru.posts.content;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** HTML tokenization and plain-text extraction for content rendering. */
final class HtmlContentSupport {

    private static final Set<String> HTML_NETWORK_ALLOWED = Set.of("b", "i", "u", "s", "a", "code", "pre", "br");

    private static final Map<String, String> NAMED_ENTITIES = Map.of(
            "amp", "&",
            "lt", "<",
            "gt", ">",
            "quot", "\"",
            "apos", "'",
            "nbsp", "\u00A0");

    private HtmlContentSupport() {}

    static boolean hasMarkupTags(String html) {
        for (int i = 0; i < html.length() - 1; i++) {
            if (html.charAt(i) == '<') {
                char next = html.charAt(i + 1);
                if ((next >= 'a' && next <= 'z') || (next >= 'A' && next <= 'Z')) {
                    return true;
                }
            }
        }
        return false;
    }

    static int plainLength(String plainText) {
        return plainText == null ? 0 : plainText.codePointCount(0, plainText.length());
    }

    static String filterAllowedTags(String html) {
        StringBuilder out = new StringBuilder(html.length());
        int i = 0;
        while (i < html.length()) {
            if (html.charAt(i) != '<') {
                out.append(html.charAt(i));
                i++;
                continue;
            }
            TagSlice tag = parseTag(html, i);
            if (tag == null) {
                out.append(html.charAt(i));
                i++;
                continue;
            }
            if (tag.allowed()) {
                out.append(tag.raw());
            }
            i = tag.endIndex();
        }
        return out.toString();
    }

    static String toPlainText(String html) {
        StringBuilder out = new StringBuilder(html.length());
        int i = 0;
        while (i < html.length()) {
            if (html.charAt(i) != '<') {
                out.append(html.charAt(i));
                i++;
                continue;
            }
            int brEnd = brTagEnd(html, i);
            if (brEnd != -1) {
                out.append('\n');
                i = brEnd;
                continue;
            }
            if (!looksLikeTag(html, i)) {
                out.append('<');
                i++;
                continue;
            }
            int close = html.indexOf('>', i + 1);
            i = close == -1 ? html.length() : close + 1;
        }
        return unescapeHtml(out.toString());
    }

    private static boolean looksLikeTag(String html, int start) {
        if (start + 1 >= html.length()) {
            return false;
        }
        char next = html.charAt(start + 1);
        return (next >= 'a' && next <= 'z') || (next >= 'A' && next <= 'Z') || next == '/' || next == '!';
    }

    private static int brTagEnd(String html, int start) {
        if (html.charAt(start) != '<') {
            return -1;
        }
        String slice = html.substring(start, Math.min(html.length(), start + 6)).toLowerCase(Locale.ROOT);
        if (!slice.startsWith("<br")) {
            return -1;
        }
        int i = start + 3;
        while (i < html.length() && (html.charAt(i) == ' ' || html.charAt(i) == '\t')) {
            i++;
        }
        if (i < html.length() && html.charAt(i) == '/') {
            i++;
        }
        if (i < html.length() && html.charAt(i) == '>') {
            return i + 1;
        }
        return -1;
    }

    private static TagSlice parseTag(String html, int start) {
        int i = start + 1;
        if (i >= html.length()) {
            return null;
        }
        boolean closing = false;
        if (html.charAt(i) == '/') {
            closing = true;
            i++;
        }
        if (i >= html.length() || !isNameChar(html.charAt(i))) {
            return null;
        }
        int nameStart = i;
        while (i < html.length() && isNameChar(html.charAt(i))) {
            i++;
        }
        String name = html.substring(nameStart, i).toLowerCase(Locale.ROOT);
        while (i < html.length() && html.charAt(i) != '>') {
            i++;
        }
        if (i >= html.length()) {
            return null;
        }
        String raw = html.substring(start, i + 1);
        boolean selfClosing = raw.endsWith("/>") || "br".equals(name);
        boolean allowed = HTML_NETWORK_ALLOWED.contains(name);
        return new TagSlice(raw, name, closing, selfClosing, allowed, i + 1);
    }

    private static boolean isNameChar(char ch) {
        return (ch >= 'a' && ch <= 'z')
                || (ch >= 'A' && ch <= 'Z')
                || (ch >= '0' && ch <= '9')
                || ch == '_'
                || ch == ':'
                || ch == '-';
    }

    private static String unescapeHtml(String input) {
        StringBuilder out = new StringBuilder(input.length());
        int i = 0;
        while (i < input.length()) {
            if (input.charAt(i) != '&') {
                out.append(input.charAt(i));
                i++;
                continue;
            }
            int semi = input.indexOf(';', i + 1);
            if (semi == -1 || semi - i > 12) {
                out.append('&');
                i++;
                continue;
            }
            String raw = input.substring(i + 1, semi);
            String decoded = decodeEntity(raw);
            if (decoded != null) {
                out.append(decoded);
                i = semi + 1;
            } else {
                out.append('&');
                i++;
            }
        }
        return out.toString();
    }

    private static String decodeEntity(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        if (raw.charAt(0) == '#') {
            try {
                int code;
                if (raw.length() > 1 && (raw.charAt(1) == 'x' || raw.charAt(1) == 'X')) {
                    code = Integer.parseInt(raw.substring(2), 16);
                } else {
                    code = Integer.parseInt(raw.substring(1));
                }
                return new String(Character.toChars(code));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return NAMED_ENTITIES.get(raw.toLowerCase(Locale.ROOT));
    }

    private record TagSlice(
            String raw, String name, boolean closing, boolean selfClosing, boolean allowed, int endIndex) {}
}
