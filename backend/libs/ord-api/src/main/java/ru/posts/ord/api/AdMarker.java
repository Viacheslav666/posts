package ru.posts.ord.api;

/** Formats the mandatory Russian ad marker line and prepends it to post text. */
public final class AdMarker {

    private AdMarker() {}

    /** Returns {@code Реклама. {advertiserName}. ИНН {inn}. erid: {erid}}. */
    public static String format(String advertiserName, String inn, String erid) {
        String name = advertiserName == null ? "" : advertiserName.trim();
        String innValue = inn == null ? "" : inn.trim();
        String eridValue = erid == null ? "" : erid.trim();
        return "Реклама. " + name + ". ИНН " + innValue + ". erid: " + eridValue;
    }

    /** Prepends {@code marker} to {@code text} with a newline when both are non-blank. */
    public static String prepend(String text, String marker) {
        boolean hasMarker = marker != null && !marker.isBlank();
        boolean hasText = text != null && !text.isBlank();
        if (!hasMarker) {
            return text == null ? "" : text;
        }
        if (!hasText) {
            return marker;
        }
        return marker + "\n" + text;
    }
}
