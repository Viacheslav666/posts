package ru.posts.social.inbound;

/** Normalized attachment reference. */
public record SocialInboundAttachment(String type, String remoteRef, String url, Long sizeBytes, String mimeType) {}
