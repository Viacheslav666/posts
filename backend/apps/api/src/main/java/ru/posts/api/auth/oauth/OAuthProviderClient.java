package ru.posts.api.auth.oauth;

/** Exchanges authorization codes (PKCE) for provider profile data. */
public interface OAuthProviderClient {

    OAuthUserProfile exchange(String provider, String code, String codeVerifier, String redirectUri);
}
