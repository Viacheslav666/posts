package ru.posts.social.api;

import java.time.Instant;

/** Access / refresh token pair returned by a network OAuth refresh call. */
public record TokenSet(String accessToken, String refreshToken, Instant expiresAt) {}
