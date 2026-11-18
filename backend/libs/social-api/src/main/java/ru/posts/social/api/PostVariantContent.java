package ru.posts.social.api;

import java.util.Locale;
import java.util.Map;

/**
 * Content payload for wall posts and stories. The single-arg constructor keeps existing call sites
 * compiling; story fields are optional and ignored by wall {@code publish}.
 */
public record PostVariantContent(
        String text, String mediaKind, String storyTtl, String linkText, String linkUrl, Map<String, Object> meta) {

    public PostVariantContent {
        meta = meta == null ? Map.of() : Map.copyOf(meta);
    }

    public PostVariantContent(String text) {
        this(text, null, null, null, null, Map.of());
    }

    public PostVariantContent withText(String newText) {
        return new PostVariantContent(newText, mediaKind, storyTtl, linkText, linkUrl, meta);
    }

    public boolean isVideo() {
        return mediaKind != null && mediaKind.toLowerCase(Locale.ROOT).contains("video");
    }
}
