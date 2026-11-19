package ru.posts.suggest.filter;

/** Machine code + human message for UI / author notification. */
public record SuggestReason(String code, String message, boolean soft) {

    public SuggestReason(String code, String message) {
        this(code, message, false);
    }

    public static SuggestReason hard(String code, String message) {
        return new SuggestReason(code, message, false);
    }

    public static SuggestReason soft(String code, String message) {
        return new SuggestReason(code, message, true);
    }
}
