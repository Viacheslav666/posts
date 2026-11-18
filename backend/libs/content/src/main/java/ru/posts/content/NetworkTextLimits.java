package ru.posts.content;

/** Text length limits for a network (from capabilities); {@code null} fields mean unknown. */
public record NetworkTextLimits(Integer maxChars, Integer captionMaxChars) {}
