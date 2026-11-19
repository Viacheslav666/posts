package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "crosspost_items")
public class CrosspostItemEntity extends BaseUuidEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "source_channel_id", nullable = false)
    private UUID sourceChannelId;

    @Column(name = "source_remote_id", nullable = false)
    private String sourceRemoteId;

    @Column(name = "source_network", nullable = false)
    private String sourceNetwork;

    @Column(nullable = false)
    private String status = "received";

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] reasons = new String[0];

    @Column(name = "post_id")
    private UUID postId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "publication_ids", nullable = false, columnDefinition = "uuid[]")
    private UUID[] publicationIds = new UUID[0];

    @Column(name = "content_text")
    private String contentText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachments_json", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> attachmentsJson = List.of();

    @Column(name = "content_fingerprint")
    private String contentFingerprint;

    @Column
    private Long simhash;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "media_hashes", nullable = false, columnDefinition = "text[]")
    private String[] mediaHashes = new String[0];

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = Map.of();

    @Column(name = "process_after", nullable = false)
    private Instant processAfter = Instant.now();

    @Column(name = "consecutive_loop_skips", nullable = false)
    private int consecutiveLoopSkips;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getRuleId() {
        return ruleId;
    }

    public void setRuleId(UUID ruleId) {
        this.ruleId = ruleId;
    }

    public UUID getSourceChannelId() {
        return sourceChannelId;
    }

    public void setSourceChannelId(UUID sourceChannelId) {
        this.sourceChannelId = sourceChannelId;
    }

    public String getSourceRemoteId() {
        return sourceRemoteId;
    }

    public void setSourceRemoteId(String sourceRemoteId) {
        this.sourceRemoteId = sourceRemoteId;
    }

    public String getSourceNetwork() {
        return sourceNetwork;
    }

    public void setSourceNetwork(String sourceNetwork) {
        this.sourceNetwork = sourceNetwork;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String[] getReasons() {
        return reasons;
    }

    public void setReasons(String[] reasons) {
        this.reasons = reasons == null ? new String[0] : reasons;
    }

    public UUID getPostId() {
        return postId;
    }

    public void setPostId(UUID postId) {
        this.postId = postId;
    }

    public UUID[] getPublicationIds() {
        return publicationIds;
    }

    public void setPublicationIds(UUID[] publicationIds) {
        this.publicationIds = publicationIds == null ? new UUID[0] : publicationIds;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public List<Map<String, Object>> getAttachmentsJson() {
        return attachmentsJson;
    }

    public void setAttachmentsJson(List<Map<String, Object>> attachmentsJson) {
        this.attachmentsJson = attachmentsJson == null ? List.of() : attachmentsJson;
    }

    public String getContentFingerprint() {
        return contentFingerprint;
    }

    public void setContentFingerprint(String contentFingerprint) {
        this.contentFingerprint = contentFingerprint;
    }

    public Long getSimhash() {
        return simhash;
    }

    public void setSimhash(Long simhash) {
        this.simhash = simhash;
    }

    public String[] getMediaHashes() {
        return mediaHashes;
    }

    public void setMediaHashes(String[] mediaHashes) {
        this.mediaHashes = mediaHashes == null ? new String[0] : mediaHashes;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload == null ? Map.of() : payload;
    }

    public Instant getProcessAfter() {
        return processAfter;
    }

    public void setProcessAfter(Instant processAfter) {
        this.processAfter = processAfter;
    }

    public int getConsecutiveLoopSkips() {
        return consecutiveLoopSkips;
    }

    public void setConsecutiveLoopSkips(int consecutiveLoopSkips) {
        this.consecutiveLoopSkips = consecutiveLoopSkips;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
