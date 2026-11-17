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
