package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "posts")
public class PostEntity extends BaseUuidEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "author_id")
    private UUID authorId;

    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "base_content", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> baseContent = Map.of();

    @Column(nullable = false)
    private String status = "draft";

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> recurrence;

    @Column(name = "rubric_id")
    private UUID rubricId;

    @Column(name = "is_ad", nullable = false)
    private boolean ad;

    @Column(name = "ord_creative_id")
    private UUID ordCreativeId;

    /** post | story — BE-16 uses story; default post for BE-9. */
    @Column(name = "content_kind", nullable = false)
    private String contentKind = "post";

    /** manual | grabber | rss | import | api — BE-17 source attribution. */
    @Column(name = "source_type", nullable = false)
    private String sourceType = "manual";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_ref", columnDefinition = "jsonb")
    private Map<String, Object> sourceRef;

    /** Snapshot of ads_items for BE-15 ORD (nullable until marking configured). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ad_payload", columnDefinition = "jsonb")
    private Map<String, Object> adPayload;

    @Column(name = "stop_at")
    private Instant stopAt;

    @Column(name = "remove_after_publish", nullable = false)
    private boolean removeAfterPublish;

    @Column(name = "story_ttl")
    private String storyTtl;

    @Column(name = "story_link_text")
    private String storyLinkText;

    @Column(name = "story_link_url")
    private String storyLinkUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "story_options", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> storyOptions = Map.of();

    /** Grabber moderation queue status (BE-17); null for manual posts. */
    @Column(name = "moderation_status")
    private String moderationStatus;

    /** IANA TZ used for recurrence materialization / calendar day boundaries. */
    @Column(nullable = false)
    private String timezone = "Europe/Moscow";

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public void setAuthorId(UUID authorId) {
        this.authorId = authorId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Map<String, Object> getBaseContent() {
        return baseContent;
    }

    public void setBaseContent(Map<String, Object> baseContent) {
        this.baseContent = baseContent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Map<String, Object> getRecurrence() {
        return recurrence;
    }

    public void setRecurrence(Map<String, Object> recurrence) {
        this.recurrence = recurrence;
    }

    public UUID getRubricId() {
        return rubricId;
    }

    public void setRubricId(UUID rubricId) {
        this.rubricId = rubricId;
    }

    public boolean isAd() {
        return ad;
    }

    public void setAd(boolean ad) {
        this.ad = ad;
    }

    public UUID getOrdCreativeId() {
        return ordCreativeId;
    }

    public void setOrdCreativeId(UUID ordCreativeId) {
        this.ordCreativeId = ordCreativeId;
    }

    public String getContentKind() {
        return contentKind;
    }

    public void setContentKind(String contentKind) {
        this.contentKind = contentKind;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Map<String, Object> getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(Map<String, Object> sourceRef) {
        this.sourceRef = sourceRef;
    }

    public Map<String, Object> getAdPayload() {
        return adPayload;
    }

    public void setAdPayload(Map<String, Object> adPayload) {
        this.adPayload = adPayload;
    }

    public Instant getStopAt() {
        return stopAt;
    }

    public void setStopAt(Instant stopAt) {
        this.stopAt = stopAt;
    }

    public boolean isRemoveAfterPublish() {
        return removeAfterPublish;
    }

    public void setRemoveAfterPublish(boolean removeAfterPublish) {
        this.removeAfterPublish = removeAfterPublish;
    }

    public String getStoryTtl() {
        return storyTtl;
    }

    public void setStoryTtl(String storyTtl) {
        this.storyTtl = storyTtl;
    }

    public String getStoryLinkText() {
        return storyLinkText;
    }

    public void setStoryLinkText(String storyLinkText) {
        this.storyLinkText = storyLinkText;
    }

    public String getStoryLinkUrl() {
        return storyLinkUrl;
    }

    public void setStoryLinkUrl(String storyLinkUrl) {
        this.storyLinkUrl = storyLinkUrl;
    }

    public Map<String, Object> getStoryOptions() {
        return storyOptions;
    }

    public void setStoryOptions(Map<String, Object> storyOptions) {
        this.storyOptions = storyOptions == null ? Map.of() : storyOptions;
    }

    public String getModerationStatus() {
        return moderationStatus;
    }

    public void setModerationStatus(String moderationStatus) {
        this.moderationStatus = moderationStatus;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
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

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
