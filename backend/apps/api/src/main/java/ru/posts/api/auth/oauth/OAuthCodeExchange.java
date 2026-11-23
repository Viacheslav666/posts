package ru.posts.api.auth.oauth;

/**
 * Authorization-code exchange input. {@code deviceId} is VK ID specific: id.vk.ru returns it on the redirect
 * together with {@code code} and requires it back on the token request.
 */
public record OAuthCodeExchange(String code, String codeVerifier, String redirectUri, String state, String deviceId) {}
