package ru.posts.suggest.filter;

import java.util.List;
import java.util.Map;

/** Suggested post snapshot fed into {@link SuggestFilterEngine}. */
public record SuggestFilterItem(
        String text, String authorVkId, List<Map<String, Object>> attachments, String contentFingerprint) {

    public SuggestFilterItem {
        text = text == null ? "" : text;
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public static SuggestFilterItem of(String text, String authorVkId, List<Map<String, Object>> attachments) {
        return new SuggestFilterItem(text, authorVkId, attachments, null);
    }
}
