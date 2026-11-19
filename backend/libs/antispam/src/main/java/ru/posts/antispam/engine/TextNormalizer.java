package ru.posts.antispam.engine;

import java.util.Locale;

/**
 * Evasion-resistant text normalization for keyword matching (BE-35).
 *
 * <ul>
 *   <li>lower-case
 *   <li>Latin ↔ Cyrillic lookalike mapping
 *   <li>strip separators inside words (@ . - _ * spaces between letters)
 *   <li>collapse repeated letters (спаааам → спам)
 * </ul>
 */
public final class TextNormalizer {

    private TextNormalizer() {}

    /** Maps common Latin lookalikes to Cyrillic equivalents (and a few reverse). */
    public static String normalizeForKeywords(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String lower = input.toLowerCase(Locale.ROOT);
        StringBuilder mapped = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); ) {
            int cp = lower.codePointAt(i);
            i += Character.charCount(cp);
            mapped.appendCodePoint(mapLookalike(cp));
        }
        String collapsedSeparators = collapseSeparators(mapped.toString());
        return collapseRepeats(collapsedSeparators);
    }

    static int mapLookalike(int cp) {
        return switch (cp) {
            case 'a' -> 'а';
            case 'e' -> 'е';
            case 'o' -> 'о';
            case 'p' -> 'р';
            case 'c' -> 'с';
            case 'x' -> 'х';
            case 'y' -> 'у';
            case 'k' -> 'к';
            case 'm' -> 'м';
            case 'h' -> 'н';
            case 'b' -> 'в';
            case 't' -> 'т';
            case '@' -> 'а';
            case '0' -> 'о';
            case '3' -> 'з';
            case '6' -> 'б';
            default -> cp;
        };
    }

    /**
     * Removes punctuation/separators that sit between letters (с-п-а-м, с п а м, с.п.а.м) while
     * keeping ordinary word boundaries (spaces between words).
     */
    static String collapseSeparators(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int len = Character.charCount(cp);
            if (isWordSeparator(cp)) {
                boolean leftLetter = hasLetterBefore(text, i);
                boolean rightLetter = hasLetterAfter(text, i + len);
                if (leftLetter && rightLetter) {
                    // skip separator glued inside a word
                    i += len;
                    continue;
                }
            }
            out.appendCodePoint(cp);
            i += len;
        }
        return out.toString();
    }

    static String collapseRepeats(String text) {
        if (text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int prev = -1;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == prev && Character.isLetter(cp)) {
                // collapse "ааа" / "aaa" → single letter
                continue;
            }
            prev = cp;
            out.appendCodePoint(cp);
        }
        return out.toString();
    }

    private static boolean isWordSeparator(int cp) {
        return !Character.isLetterOrDigit(cp);
    }

    private static boolean hasLetterBefore(String text, int index) {
        for (int i = index; i > 0; ) {
            int cp = text.codePointBefore(i);
            i -= Character.charCount(cp);
            if (Character.isLetter(cp)) {
                return true;
            }
            if (!isWordSeparator(cp)) {
                return false;
            }
        }
        return false;
    }

    private static boolean hasLetterAfter(String text, int index) {
        for (int i = index; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetter(cp)) {
                return true;
            }
            if (!isWordSeparator(cp)) {
                return false;
            }
        }
        return false;
    }
}
