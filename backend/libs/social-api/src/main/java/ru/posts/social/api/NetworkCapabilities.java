package ru.posts.social.api;

import java.util.Map;

/** Declared limits and feature flags for a social network adapter. */
public record NetworkCapabilities(
        Network network,
        boolean supportsDelete,
        boolean supportsUploadMedia,
        boolean supportsTokenRefresh,
        Map<String, Boolean> features) {

    public NetworkCapabilities {
        features = features == null ? Map.of() : Map.copyOf(features);
    }

    public static NetworkCapabilities minimal(Network network) {
        return new NetworkCapabilities(network, false, false, false, Map.of());
    }
}
