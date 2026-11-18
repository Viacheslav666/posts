package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ord_contractors")
public class OrdContractorEntity extends BaseUuidEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String inn;

    @Column(nullable = false)
    private String kpp = "";

    @Column(nullable = false)
    private String name;

    @Column(name = "org_type", nullable = false)
    private String orgType;

    @Column(name = "country_oksm", nullable = false)
    private String countryOksm = "643";

    private String phone;

    private String address;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> meta = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_refs", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> providerRefs = Map.of();

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

    public String getInn() {
        return inn;
    }

    public void setInn(String inn) {
        this.inn = inn;
    }

    public String getKpp() {
        return kpp;
    }

    public void setKpp(String kpp) {
        this.kpp = kpp == null ? "" : kpp;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrgType() {
        return orgType;
    }

    public void setOrgType(String orgType) {
        this.orgType = orgType;
    }

    public String getCountryOksm() {
        return countryOksm;
    }

    public void setCountryOksm(String countryOksm) {
        this.countryOksm = countryOksm;
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

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta == null ? Map.of() : meta;
    }

    public Map<String, Object> getProviderRefs() {
        return providerRefs;
    }

    public void setProviderRefs(Map<String, Object> providerRefs) {
        this.providerRefs = providerRefs == null ? Map.of() : providerRefs;
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
