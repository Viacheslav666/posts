package ru.posts.social.inbound.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.social-inbound")
public class SocialInboundProperties {

    private String publicBaseUrl = "http://127.0.0.1:8080";
    private String socialBaseUrl = "http://127.0.0.1:8090";
    private String vkApiVersion = "5.199";
    private boolean recordingHandler = false;
    private int retentionDays = 14;
    private int partitionAheadDays = 7;
    private int hookBodyMaxBytes = 1_048_576;

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getSocialBaseUrl() {
        return socialBaseUrl;
    }

    public void setSocialBaseUrl(String socialBaseUrl) {
        this.socialBaseUrl = socialBaseUrl;
    }

    public String getVkApiVersion() {
        return vkApiVersion;
    }

    public void setVkApiVersion(String vkApiVersion) {
        this.vkApiVersion = vkApiVersion;
    }

    public boolean isRecordingHandler() {
        return recordingHandler;
    }

    public void setRecordingHandler(boolean recordingHandler) {
        this.recordingHandler = recordingHandler;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public int getPartitionAheadDays() {
        return partitionAheadDays;
    }

    public void setPartitionAheadDays(int partitionAheadDays) {
        this.partitionAheadDays = partitionAheadDays;
    }

    public int getHookBodyMaxBytes() {
        return hookBodyMaxBytes;
    }

    public void setHookBodyMaxBytes(int hookBodyMaxBytes) {
        this.hookBodyMaxBytes = hookBodyMaxBytes;
    }

    public String trimmedPublicBaseUrl() {
        String v = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
