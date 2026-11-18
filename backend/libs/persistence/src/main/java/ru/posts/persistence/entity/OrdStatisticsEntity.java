package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ord_statistics")
public class OrdStatisticsEntity extends BaseUuidEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "creative_id", nullable = false)
    private UUID creativeId;

    @Column(name = "platform_id")
    private UUID platformId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "imps_fact", nullable = false)
    private long impsFact;

    private BigDecimal amount;

    @Column(nullable = false)
    private String status = "draft";

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> meta = Map.of();

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

    public UUID getCreativeId() {
        return creativeId;
    }

    public void setCreativeId(UUID creativeId) {
        this.creativeId = creativeId;
    }

    public UUID getPlatformId() {
        return platformId;
    }

    public void setPlatformId(UUID platformId) {
        this.platformId = platformId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getPeriodYear() {
        return periodYear;
    }

    public void setPeriodYear(int periodYear) {
        this.periodYear = periodYear;
    }

    public int getPeriodMonth() {
        return periodMonth;
    }

    public void setPeriodMonth(int periodMonth) {
        this.periodMonth = periodMonth;
    }

    public long getImpsFact() {
        return impsFact;
    }

    public void setImpsFact(long impsFact) {
        this.impsFact = impsFact;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta == null ? Map.of() : meta;
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
