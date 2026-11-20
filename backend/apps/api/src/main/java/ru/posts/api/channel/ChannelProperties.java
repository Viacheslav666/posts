package ru.posts.api.channel;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.channels")
public class ChannelProperties {

    private String socialBaseUrl = "http://127.0.0.1:8090";
    private String socialPublicBaseUrl;
    private String appBaseUrl = "http://localhost:3000";
    private String vkAuthorizationUri;
    private String vkTokenUri;
    private String vkClientId = "local-vk-channels";
    private String vkClientSecret = "local-vk-channels-secret";
    private String vkRedirectUri;
    private String okAuthorizationUri;
    private String okTokenUri;
    private String okClientId = "local-ok-channels";
    private String okClientSecret = "local-ok-channels-secret";
    private String okRedirectUri;
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

    public String socialPublicBaseUrl() {
        return firstNonBlank(socialPublicBaseUrl, socialBaseUrl);
    }

    public void setSocialPublicBaseUrl(String socialPublicBaseUrl) {
        this.socialPublicBaseUrl = socialPublicBaseUrl;
    }

    public String appBaseUrl() {
        return appBaseUrl;
    }

    public void setAppBaseUrl(String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    public String vkAuthorizationUri() {
        return firstNonBlank(vkAuthorizationUri, socialPublicBaseUrl() + "/oauth/vk/authorize");
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
        return firstNonBlank(okAuthorizationUri, socialPublicBaseUrl() + "/oauth/ok/authorize");
    }

    public String channelConnectRedirectUri(String network, UUID workspaceId) {
        if ("vk".equals(network) && vkRedirectUri != null && !vkRedirectUri.isBlank()) {
            return vkRedirectUri;
        }
        if ("ok".equals(network) && okRedirectUri != null && !okRedirectUri.isBlank()) {
            return okRedirectUri;
        }
        String base = appBaseUrl().endsWith("/")
                ? appBaseUrl().substring(0, appBaseUrl().length() - 1)
                : appBaseUrl();
        return base + "/app/" + workspaceId + "/channels/connect/" + network + "/callback";
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
