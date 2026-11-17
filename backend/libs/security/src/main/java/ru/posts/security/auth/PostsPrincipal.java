package ru.posts.security.auth;

import java.util.UUID;

/** Authenticated API principal extracted from the access JWT. */
public record PostsPrincipal(UUID userId, UUID sessionId) {}
