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
@Table(name = "grabber_items")
public class GrabberItemEntity extends BaseUuidEntity {

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "target_channel_id", nullable = false)
    private UUID targetChannelId;

    @Column(name = "source_network", nullable = false)
    private String sourceNetwork;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(name = "remote_post_id", nullable = false)
    private String remotePostId;

    @Column(name = "source_permalink")
    private String sourcePermalink;

    @Column(name = "source_name")
    private String sourceName;

    @Column(name = "content_text")
    private String contentText;

    @Column(name = "content_html")
    private String contentHtml;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachments_json", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> attachmentsJson = List.of();

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "simhash")
    private Long simhash;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "media_hashes", nullable = false, columnDefinition = "text[]")
    private String[] mediaHashes = new String[0];

    @Column(nullable = false)
    private String status;

    @Column(name = "skip_reason")
    private String skipReason;

    @Column(name = "post_id")
    private UUID postId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "publication_ids", nullable = false, columnDefinition = "uuid[]")
    private UUID[] publicationIds = new UUID[0];

    @Column(name = "moderated_by")
    private UUID moderatedBy;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "edited_text")
    private String editedText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private Map<String, Object> rawPayload;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getRuleId() {
        return ruleId;
    }

    public void setRuleId(UUID ruleId) {
        this.ruleId = ruleId;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getTargetChannelId() {
        return targetChannelId;
    }

    public void setTargetChannelId(UUID targetChannelId) {
        this.targetChannelId = targetChannelId;
    }

    public String getSourceNetwork() {
        return sourceNetwork;
    }

    public void setSourceNetwork(String sourceNetwork) {
        this.sourceNetwork = sourceNetwork;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getRemotePostId() {
        return remotePostId;
    }

    public void setRemotePostId(String remotePostId) {
        this.remotePostId = remotePostId;
    }

    public String getSourcePermalink() {
        return sourcePermalink;
    }

    public void setSourcePermalink(String sourcePermalink) {
        this.sourcePermalink = sourcePermalink;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public String getContentHtml() {
        return contentHtml;
    }

    public void setContentHtml(String contentHtml) {
        this.contentHtml = contentHtml;
    }

    public List<Map<String, Object>> getAttachmentsJson() {
        return attachmentsJson;
    }

    public void setAttachmentsJson(List<Map<String, Object>> attachmentsJson) {
        this.attachmentsJson = attachmentsJson;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
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
        this.mediaHashes = mediaHashes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public void setSkipReason(String skipReason) {
        this.skipReason = skipReason;
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
        this.publicationIds = publicationIds;
    }

    public UUID getModeratedBy() {
        return moderatedBy;
    }

    public void setModeratedBy(UUID moderatedBy) {
        this.moderatedBy = moderatedBy;
    }

    public Instant getModeratedAt() {
        return moderatedAt;
    }

    public void setModeratedAt(Instant moderatedAt) {
        this.moderatedAt = moderatedAt;
    }

    public String getEditedText() {
        return editedText;
    }

    public void setEditedText(String editedText) {
        this.editedText = editedText;
    }

    public Map<String, Object> getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(Map<String, Object> rawPayload) {
        this.rawPayload = rawPayload;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
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
