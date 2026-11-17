package ru.posts.api.auth.oauth;

import java.util.Map;

public record OAuthUserProfile(
        String provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String name,
        Map<String, Object> raw) {}
