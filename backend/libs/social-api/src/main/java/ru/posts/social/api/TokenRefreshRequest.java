package ru.posts.social.api;

/** Request payload for refreshing a social network OAuth token. */
public record TokenRefreshRequest(
        String refreshToken, String network, String externalUserId, String clientId, String deviceId, String state) {

    public TokenRefreshRequest(String refreshToken, String network, String externalUserId) {
        this(refreshToken, network, externalUserId, null, null, null);
    }
}
