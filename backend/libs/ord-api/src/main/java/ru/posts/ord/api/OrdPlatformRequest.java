package ru.posts.ord.api;

/** Platform / pad registration payload (channel → ORD pad). */
public record OrdPlatformRequest(String externalId, String name, String url, String network, boolean isOwner) {}
