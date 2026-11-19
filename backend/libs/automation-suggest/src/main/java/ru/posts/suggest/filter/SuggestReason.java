package ru.posts.suggest.filter;

/** Machine code + human-readable (RU) message for UI / author notifications. */
public record SuggestReason(String code, String message) {

    public SuggestReason {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code required");
        }
        message = message == null ? "" : message;
    }
}
