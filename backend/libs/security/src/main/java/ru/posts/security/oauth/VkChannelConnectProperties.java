package ru.posts.security.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * VK channel-connect settings shared by api, publisher, and ingest. Independent from Telegram / OK / Max base URLs.
 */
@ConfigurationProperties(prefix = "posts.channels")
public class VkChannelConnectProperties {

    private String socialBaseUrl = "http://127.0.0.1:8090";
    private String vkAuthorizationUri;
    private String vkTokenUri;
    private String vkClientId = "local-vk-channels";

    public String socialBaseUrl() {
        return socialBaseUrl;
    }

    public void setSocialBaseUrl(String socialBaseUrl) {
        this.socialBaseUrl = socialBaseUrl;
    }

    public String vkAuthorizationUri() {
        return vkAuthorizationUri == null || vkAuthorizationUri.isBlank()
                ? trim(socialBaseUrl) + "/oauth/vk/authorize"
                : vkAuthorizationUri;
    }

    public void setVkAuthorizationUri(String vkAuthorizationUri) {
        this.vkAuthorizationUri = vkAuthorizationUri;
    }

    public String vkTokenUri() {
        return vkTokenUri == null || vkTokenUri.isBlank() ? trim(socialBaseUrl) + "/oauth/vk/token" : vkTokenUri;
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

    public boolean vkIdMode() {
        String tokenUri = vkTokenUri();
        String authUri = vkAuthorizationUri();
        return tokenUri.contains("oauth2/auth") || tokenUri.contains("id.vk.ru") || authUri.contains("id.vk.ru");
    }

    private static String trim(String url) {
        if (url == null || url.isBlank()) {
            return "http://127.0.0.1:8090";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
