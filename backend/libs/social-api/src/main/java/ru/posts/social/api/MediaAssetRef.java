package ru.posts.social.api;

/**
 * Lightweight media asset descriptor for adapters (not a JPA entity). Bytes are resolved by the
 * caller or storage layer before upload when needed.
 */
public record MediaAssetRef(String id, String kind, String mime, long sizeBytes, String sha256) {}
