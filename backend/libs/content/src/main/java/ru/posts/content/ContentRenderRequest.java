package ru.posts.content;

/** Input for per-network content render and validation. */
public record ContentRenderRequest(String network, String htmlOrText, boolean hasMedia, NetworkTextLimits limits) {

    public ContentRenderRequest(String network, String htmlOrText, boolean hasMedia) {
        this(network, htmlOrText, hasMedia, new NetworkTextLimits(null, null));
    }
}
