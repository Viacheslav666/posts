package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promo_redemptions")
public class PromoRedemptionEntity extends BaseUuidEntity {

    @Column(name = "promo_code_id", nullable = false)
    private UUID promoCodeId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getPromoCodeId() {
        return promoCodeId;
    }

    public void setPromoCodeId(UUID promoCodeId) {
        this.promoCodeId = promoCodeId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
