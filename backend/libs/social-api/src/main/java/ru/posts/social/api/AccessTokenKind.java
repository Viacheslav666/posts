package ru.posts.social.api;

/** Which credential an adapter method needs (VK user vs community; TG/Max bot). */
public enum AccessTokenKind {
    USER,
    COMMUNITY,
    BOT
}
