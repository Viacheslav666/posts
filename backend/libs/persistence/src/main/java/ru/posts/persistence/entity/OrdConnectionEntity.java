package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ord_connections")
public class OrdConnectionEntity extends BaseUuidEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "api_key_enc", nullable = false)
    private byte[] apiKeyEnc;

    @Column(name = "publisher_inn")
    private String publisherInn;

    @Column(name = "publisher_name")
    private String publisherName;

    @Column(name = "org_type")
    private String orgType;

    private String phone;

    private String address;

    @Column(name = "vat_rate", nullable = false)
    private String vatRate = "without_vat";

    @Column(name = "is_foreign", nullable = false)
    private boolean foreign;

    @Column(name = "notify_problems", nullable = false)
    private boolean notifyProblems = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> roles = List.of();

    @Column(nullable = false)
    private String status = "draft";

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public byte[] getApiKeyEnc() {
        return apiKeyEnc;
    }

    public void setApiKeyEnc(byte[] apiKeyEnc) {
        this.apiKeyEnc = apiKeyEnc;
    }

    public String getPublisherInn() {
        return publisherInn;
    }

    public void setPublisherInn(String publisherInn) {
        this.publisherInn = publisherInn;
    }

    public String getPublisherName() {
        return publisherName;
    }

    public void setPublisherName(String publisherName) {
        this.publisherName = publisherName;
    }

    public String getOrgType() {
        return orgType;
    }

    public void setOrgType(String orgType) {
        this.orgType = orgType;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getVatRate() {
        return vatRate;
    }

    public void setVatRate(String vatRate) {
        this.vatRate = vatRate == null || vatRate.isBlank() ? "without_vat" : vatRate;
    }

    public boolean isForeign() {
        return foreign;
    }

    public void setForeign(boolean foreign) {
        this.foreign = foreign;
    }

    public boolean isNotifyProblems() {
        return notifyProblems;
    }

    public void setNotifyProblems(boolean notifyProblems) {
        this.notifyProblems = notifyProblems;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles == null ? List.of() : roles;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
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
