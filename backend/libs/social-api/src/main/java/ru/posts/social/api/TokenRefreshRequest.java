package ru.posts.social.api;

/** Request payload for refreshing a social network OAuth token. */
public record TokenRefreshRequest(String refreshToken, String network, String externalUserId) {}
