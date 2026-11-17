package ru.posts.api.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.channels")
public class ChannelProperties {

    private String socialBaseUrl = "http://127.0.0.1:8090";
    private String vkAuthorizationUri;
    private String vkTokenUri;
    private String vkClientId = "local-vk-channels";
    private String vkClientSecret = "local-vk-channels-secret";
    private String vkRedirectUri = "http://localhost:3000/channels/callback/vk";
    private String okAuthorizationUri;
    private String okTokenUri;
    private String okClientId = "local-ok-channels";
    private String okClientSecret = "local-ok-channels-secret";
    private String okRedirectUri = "http://localhost:3000/channels/callback/ok";
    private String okPublicKey = "local-ok-public";
    private String okApplicationKey = "local-ok-app";
    private String tgBotUsername = "posts_local_bot";
    private String tgBotToken = "emulator-token";
    private String maxBotToken = "emulator-max-token";
    private String maxBaseUrl;
    private String ingestExchange = "ingest";
    private String ingestRoutingKey = "social";

    public String socialBaseUrl() {
        return socialBaseUrl;
    }

    public void setSocialBaseUrl(String socialBaseUrl) {
        this.socialBaseUrl = socialBaseUrl;
    }

    public String vkAuthorizationUri() {
        return firstNonBlank(vkAuthorizationUri, socialBaseUrl + "/oauth/vk/authorize");
    }

    public void setVkAuthorizationUri(String vkAuthorizationUri) {
        this.vkAuthorizationUri = vkAuthorizationUri;
    }

    public String vkTokenUri() {
        return firstNonBlank(vkTokenUri, socialBaseUrl + "/oauth/vk/token");
    }

    public void setVkTokenUri(String vkTokenUri) {
        this.vkTokenUri = vkTokenUri;
    }

    public String vkClientId() {
        return vkClientId;
    }

    public void setVkClientId(String vkClientId) {
        this.vkClientId = vkClientId;
    }

    public String vkClientSecret() {
        return vkClientSecret;
    }

    public void setVkClientSecret(String vkClientSecret) {
        this.vkClientSecret = vkClientSecret;
    }

    public String vkRedirectUri() {
        return vkRedirectUri;
    }

    public void setVkRedirectUri(String vkRedirectUri) {
        this.vkRedirectUri = vkRedirectUri;
    }

    public String okAuthorizationUri() {
        return firstNonBlank(okAuthorizationUri, socialBaseUrl + "/oauth/ok/authorize");
    }

    public void setOkAuthorizationUri(String okAuthorizationUri) {
        this.okAuthorizationUri = okAuthorizationUri;
    }

    public String okTokenUri() {
        return firstNonBlank(okTokenUri, socialBaseUrl + "/oauth/ok/token");
    }

    public void setOkTokenUri(String okTokenUri) {
        this.okTokenUri = okTokenUri;
    }

    public String okClientId() {
        return okClientId;
    }

    public void setOkClientId(String okClientId) {
        this.okClientId = okClientId;
    }

    public String okClientSecret() {
        return okClientSecret;
    }

    public void setOkClientSecret(String okClientSecret) {
        this.okClientSecret = okClientSecret;
    }

    public String okRedirectUri() {
        return okRedirectUri;
    }

    public void setOkRedirectUri(String okRedirectUri) {
        this.okRedirectUri = okRedirectUri;
    }

    public String okPublicKey() {
        return okPublicKey;
    }

    public void setOkPublicKey(String okPublicKey) {
        this.okPublicKey = okPublicKey;
    }

    public String okApplicationKey() {
        return okApplicationKey;
    }

    public void setOkApplicationKey(String okApplicationKey) {
        this.okApplicationKey = okApplicationKey;
    }

    public String tgBotUsername() {
        return tgBotUsername;
    }

    public void setTgBotUsername(String tgBotUsername) {
        this.tgBotUsername = tgBotUsername;
    }

    public String tgBotToken() {
        return tgBotToken;
    }

    public void setTgBotToken(String tgBotToken) {
        this.tgBotToken = tgBotToken;
    }

    public String maxBotToken() {
        return maxBotToken;
    }

    public void setMaxBotToken(String maxBotToken) {
        this.maxBotToken = maxBotToken;
    }

    public String maxBaseUrl() {
        return firstNonBlank(maxBaseUrl, socialBaseUrl);
    }

    public void setMaxBaseUrl(String maxBaseUrl) {
        this.maxBaseUrl = maxBaseUrl;
    }

    public String ingestExchange() {
        return ingestExchange;
    }

    public void setIngestExchange(String ingestExchange) {
        this.ingestExchange = ingestExchange;
    }

    public String ingestRoutingKey() {
        return ingestRoutingKey;
    }

    public void setIngestRoutingKey(String ingestRoutingKey) {
        this.ingestRoutingKey = ingestRoutingKey;
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
