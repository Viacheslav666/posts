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
@Table(name = "ord_creatives")
public class OrdCreativeEntity extends BaseUuidEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "contract_id")
    private UUID contractId;

    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "connection_id")
    private UUID connectionId;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    private String erid;

    @Column(nullable = false)
    private String provider;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] kktu = new String[0];

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "media_refs", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> mediaRefs = List.of();

    @Column(name = "advertiser_name")
    private String advertiserName;

    @Column(name = "advertiser_inn")
    private String advertiserInn;

    @Column(nullable = false)
    private String status = "draft";

    @Column(name = "registered_at")
    private Instant registeredAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "display_period_days", nullable = false)
    private int displayPeriodDays = 30;

    @Column(name = "auto_delete_after_period", nullable = false)
    private boolean autoDeleteAfterPeriod = true;

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

    public UUID getContractId() {
        return contractId;
    }

    public void setContractId(UUID contractId) {
        this.contractId = contractId;
    }

    public UUID getPostId() {
        return postId;
    }

    public void setPostId(UUID postId) {
        this.postId = postId;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(UUID connectionId) {
        this.connectionId = connectionId;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getErid() {
        return erid;
    }

    public void setErid(String erid) {
        this.erid = erid;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String[] getKktu() {
        return kktu;
    }

    public void setKktu(String[] kktu) {
        this.kktu = kktu == null ? new String[0] : kktu;
    }

    public List<Map<String, Object>> getMediaRefs() {
        return mediaRefs;
    }

    public void setMediaRefs(List<Map<String, Object>> mediaRefs) {
        this.mediaRefs = mediaRefs == null ? List.of() : mediaRefs;
    }

    public String getAdvertiserName() {
        return advertiserName;
    }

    public void setAdvertiserName(String advertiserName) {
        this.advertiserName = advertiserName;
    }

    public String getAdvertiserInn() {
        return advertiserInn;
    }

    public void setAdvertiserInn(String advertiserInn) {
        this.advertiserInn = advertiserInn;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public int getDisplayPeriodDays() {
        return displayPeriodDays;
    }

    public void setDisplayPeriodDays(int displayPeriodDays) {
        this.displayPeriodDays = displayPeriodDays;
    }

    public boolean isAutoDeleteAfterPeriod() {
        return autoDeleteAfterPeriod;
    }

    public void setAutoDeleteAfterPeriod(boolean autoDeleteAfterPeriod) {
        this.autoDeleteAfterPeriod = autoDeleteAfterPeriod;
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
