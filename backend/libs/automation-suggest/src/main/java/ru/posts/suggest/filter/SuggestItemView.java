package ru.posts.suggest.filter;

import java.util.List;
import java.util.Map;

/** Immutable view of a suggested post for filter evaluation. */
public record SuggestItemView(
        String vkPostId,
        String authorVkId,
        String text,
        List<Map<String, Object>> attachments,
        Long simhash,
        boolean markedAsAds,
        boolean hasErid) {

    public SuggestItemView {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public static SuggestItemView of(
            String vkPostId, String authorVkId, String text, List<Map<String, Object>> attachments, Long simhash) {
        return new SuggestItemView(vkPostId, authorVkId, text, attachments, simhash, false, false);
    }
}
