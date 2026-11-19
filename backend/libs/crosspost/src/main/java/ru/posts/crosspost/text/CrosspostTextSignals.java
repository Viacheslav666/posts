package ru.posts.crosspost.text;

import java.util.Locale;
import java.util.regex.Pattern;

/** Pure text helpers for crosspost filters/transforms. */
public final class CrosspostTextSignals {

    private static final Pattern URL =
            Pattern.compile("(?i)\\b((?:https?://|www\\.)[^\\s<>\\[\\]()\"']+|vk\\.cc/\\S+|t\\.me/\\S+|max\\.ru/\\S+)");
    private static final Pattern HASHTAG = Pattern.compile("(?u)#[\\p{L}\\p{N}_]+");
    private static final Pattern ERID = Pattern.compile("(?i)(?:^|[^a-z0-9_])erid(?:[=:\\s]+[a-z0-9_-]+|\\b)");
    private static final Pattern AD_MARK =
            Pattern.compile("(?iu)(?:^|[^\\p{L}\\p{N}_])(?:реклама|reklama)(?:$|[^\\p{L}\\p{N}_])");

    private CrosspostTextSignals() {}

    public static boolean isBlankText(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String stripped = HASHTAG.matcher(text).replaceAll("").replaceAll("\\s+", "");
        return stripped.isEmpty();
    }

    public static boolean containsLink(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return URL.matcher(text).find();
    }

    public static boolean containsErid(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return ERID.matcher(text).find();
    }

    public static boolean containsAdMark(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return AD_MARK.matcher(text).find();
    }

    public static String removeLinks(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        return URL.matcher(text).replaceAll(" ").replaceAll("\\s{2,}", " ").trim();
    }

    public static String removeHashtags(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        return HASHTAG.matcher(text).replaceAll(" ").replaceAll("\\s{2,}", " ").trim();
    }

    public static String truncateWithReadMore(String text, int max, String sourceUrl) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        int cut = Math.max(0, max - 40);
        String head = text.substring(0, cut).trim();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return head + "…";
        }
        return head + "… Читать полностью: " + sourceUrl;
    }

    public static boolean looksLikeAd(String text, boolean markedAsAds, boolean hasErid) {
        if (markedAsAds || hasErid) {
            return true;
        }
        if (containsErid(text) || containsAdMark(text)) {
            return true;
        }
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return lower.contains("erid=");
    }
}
