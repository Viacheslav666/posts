package ru.posts.ord.api;

/**
 * ORD API credentials. {@code apiKey} is plaintext only in memory (never log / persist from this
 * type).
 */
public record OrdCredentials(String apiKey) {
    public OrdCredentials {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey is required");
        }
    }
}
