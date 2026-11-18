package ru.posts.grabber.text;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure text detectors for grabber skip-filters (phones, caps, links, homoglyphs). */
public final class TextSignals {

    private static final Pattern URL =
            Pattern.compile("(?i)\\b((?:https?://|www\\.)[^\\s<>\\[\\]()\"']+|vk\\.cc/\\S+|youla\\.ru/\\S+)");
    private static final Pattern HASHTAG = Pattern.compile("(?u)#[\\p{L}\\p{N}_]+");
    private static final Pattern MENTION = Pattern.compile("(?u)@[\\p{L}\\p{N}._]+");
    /** RU mobile + international (+7 / 8 / +NNN) with spaces/dashes/parens. */
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:\\+?7|8)?[\\s\\-()]*(?:9\\d{2}|\\d{3})[\\s\\-()]*\\d{3}[\\s\\-]*\\d{2}[\\s\\-]*\\d{2}(?!\\d)"
                    + "|(?<!\\d)\\+\\d{1,3}[\\s\\-()]*\\d{2,4}[\\s\\-()]*\\d{2,4}[\\s\\-()]*\\d{2,4}(?!\\d)");

    /** Latin lookalikes that commonly substitute Cyrillic letters. */
    private static final String LATIN_HOMOGLYPHS = "aAeEoOpPcCxXyYkKmMhHbBtT";

    private TextSignals() {}

    public static boolean isBlankText(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String stripped = HASHTAG.matcher(MENTION.matcher(text).replaceAll("")).replaceAll("");
        return stripped.replaceAll("\\s+", "").isEmpty();
    }

    public static boolean containsLink(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("youla.ru") || lower.contains("vk.cc/")) {
            return true;
        }
        return URL.matcher(text).find();
    }

    public static boolean containsPhone(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return PHONE.matcher(text).find();
    }

    /**
     * Caps ratio among letters. Returns 0 when fewer than {@code minLetters} letters present.
     * Threshold comparison is caller's job.
     */
    public static double capsRatio(String text, int minLetters) {
        if (text == null || text.isBlank()) {
            return 0d;
        }
        int letters = 0;
        int upper = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) {
                continue;
            }
            letters++;
            if (Character.isUpperCase(cp)) {
                upper++;
            }
        }
        if (letters < minLetters) {
            return 0d;
        }
        return (double) upper / (double) letters;
    }

    /**
     * Detects Latin characters used where a Cyrillic word is expected: mixed script tokens
     * containing both Cyrillic and Latin lookalike letters (e.g. «Рeклама» with Latin e).
     */
    public static boolean hasHomoglyphSubstitution(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        Matcher token = Pattern.compile("(?u)[\\p{L}]+").matcher(text);
        while (token.find()) {
            String word = token.group();
            boolean hasCyr = false;
            boolean hasLatinLookalike = false;
            for (int i = 0; i < word.length(); ) {
                int cp = word.codePointAt(i);
                i += Character.charCount(cp);
                Character.UnicodeScript script = Character.UnicodeScript.of(cp);
                if (script == Character.UnicodeScript.CYRILLIC) {
                    hasCyr = true;
                } else if (script == Character.UnicodeScript.LATIN) {
                    if (LATIN_HOMOGLYPHS.indexOf(cp) >= 0 || Character.isLetter(cp)) {
                        hasLatinLookalike = true;
                    }
                }
            }
            if (hasCyr && hasLatinLookalike) {
                return true;
            }
        }
        return false;
    }

    public static String stripPhones(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        return PHONE.matcher(text).replaceAll("").replaceAll(" {2,}", " ").trim();
    }

    public static String stripLinks(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        return URL.matcher(text).replaceAll("").replaceAll(" {2,}", " ").trim();
    }

    public static String stripHashtags(String text) {
        if (text == null) {
            return "";
        }
        return HASHTAG.matcher(text).replaceAll("").replaceAll(" {2,}", " ").trim();
    }

    public static String stripMentions(String text) {
        if (text == null) {
            return "";
        }
        return MENTION.matcher(text).replaceAll("").replaceAll(" {2,}", " ").trim();
    }
}
