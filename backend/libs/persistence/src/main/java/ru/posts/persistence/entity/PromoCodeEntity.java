package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "promo_codes")
public class PromoCodeEntity extends BaseUuidEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private BigDecimal value;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "redemption_count", nullable = false)
    private int redemptionCount;

    @Column(nullable = false)
    private boolean stackable;

    @Column(name = "once_per_workspace", nullable = false)
    private boolean oncePerWorkspace = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(Instant validUntil) {
        this.validUntil = validUntil;
    }

    public Integer getMaxRedemptions() {
        return maxRedemptions;
    }

    public void setMaxRedemptions(Integer maxRedemptions) {
        this.maxRedemptions = maxRedemptions;
    }

    public int getRedemptionCount() {
        return redemptionCount;
    }

    public void setRedemptionCount(int redemptionCount) {
        this.redemptionCount = redemptionCount;
    }

    public boolean isStackable() {
        return stackable;
    }

    public void setStackable(boolean stackable) {
        this.stackable = stackable;
    }

    public boolean isOncePerWorkspace() {
        return oncePerWorkspace;
    }

    public void setOncePerWorkspace(boolean oncePerWorkspace) {
        this.oncePerWorkspace = oncePerWorkspace;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
