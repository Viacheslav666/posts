package ru.posts.social.inbound;

/** Normalized author of an inbound event. */
public record SocialInboundAuthor(String id, boolean community, boolean bot) {}
