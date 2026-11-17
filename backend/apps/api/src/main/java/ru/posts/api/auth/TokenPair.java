package ru.posts.api.auth;

import java.util.UUID;

public record TokenPair(String accessToken, long expiresIn, String refreshToken, UUID sessionId) {}
