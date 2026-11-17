package ru.posts.api.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.channels")
public record ChannelProperties(
        String socialBaseUrl,
        String vkAuthorizationUri,
        String vkTokenUri,
        String vkClientId,
        String vkClientSecret,
        String vkRedirectUri,
        String okAuthorizationUri,
        String okTokenUri,
        String okClientId,
        String okClientSecret,
        String okRedirectUri,
        String okPublicKey,
        String okApplicationKey,
        String tgBotUsername,
        String tgBotToken,
        String maxBotToken,
        String maxBaseUrl,
        String ingestExchange,
        String ingestRoutingKey) {

    public ChannelProperties {
        if (socialBaseUrl == null || socialBaseUrl.isBlank()) {
            socialBaseUrl = "http://127.0.0.1:8090";
        }
        if (vkAuthorizationUri == null || vkAuthorizationUri.isBlank()) {
            vkAuthorizationUri = socialBaseUrl + "/oauth/vk/authorize";
        }
        if (vkTokenUri == null || vkTokenUri.isBlank()) {
            vkTokenUri = socialBaseUrl + "/oauth/vk/token";
        }
        if (vkClientId == null || vkClientId.isBlank()) {
            vkClientId = "local-vk-channels";
        }
        if (vkClientSecret == null || vkClientSecret.isBlank()) {
            vkClientSecret = "local-vk-channels-secret";
        }
        if (vkRedirectUri == null || vkRedirectUri.isBlank()) {
            vkRedirectUri = "http://localhost:3000/channels/callback/vk";
        }
        if (okAuthorizationUri == null || okAuthorizationUri.isBlank()) {
            okAuthorizationUri = socialBaseUrl + "/oauth/ok/authorize";
        }
        if (okTokenUri == null || okTokenUri.isBlank()) {
            okTokenUri = socialBaseUrl + "/oauth/ok/token";
        }
        if (okClientId == null || okClientId.isBlank()) {
            okClientId = "local-ok-channels";
        }
        if (okClientSecret == null || okClientSecret.isBlank()) {
            okClientSecret = "local-ok-channels-secret";
        }
        if (okRedirectUri == null || okRedirectUri.isBlank()) {
            okRedirectUri = "http://localhost:3000/channels/callback/ok";
        }
        if (okPublicKey == null || okPublicKey.isBlank()) {
            okPublicKey = "local-ok-public";
        }
        if (okApplicationKey == null || okApplicationKey.isBlank()) {
            okApplicationKey = "local-ok-app";
        }
        if (tgBotUsername == null || tgBotUsername.isBlank()) {
            tgBotUsername = "posts_local_bot";
        }
        if (tgBotToken == null || tgBotToken.isBlank()) {
            tgBotToken = "emulator-token";
        }
        if (maxBotToken == null || maxBotToken.isBlank()) {
            maxBotToken = "emulator-max-token";
        }
        if (maxBaseUrl == null || maxBaseUrl.isBlank()) {
            maxBaseUrl = socialBaseUrl;
        }
        if (ingestExchange == null || ingestExchange.isBlank()) {
            ingestExchange = "ingest";
        }
        if (ingestRoutingKey == null || ingestRoutingKey.isBlank()) {
            ingestRoutingKey = "social";
        }
    }
}
