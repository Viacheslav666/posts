package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "autopay_settings")
public class AutopaySettingsEntity {

    @Id
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "threshold_kopecks", nullable = false)
    private long thresholdKopecks = 10_000L;

    @Column(name = "topup_amount_kopecks", nullable = false)
    private long topupAmountKopecks = 50_000L;

    @Column(name = "provider_payment_method_id")
    private String providerPaymentMethodId;

    @Column(name = "card_mask")
    private String cardMask;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "last_payment_id")
    private UUID lastPaymentId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getThresholdKopecks() {
        return thresholdKopecks;
    }

    public void setThresholdKopecks(long thresholdKopecks) {
        this.thresholdKopecks = thresholdKopecks;
    }

    public long getTopupAmountKopecks() {
        return topupAmountKopecks;
    }

    public void setTopupAmountKopecks(long topupAmountKopecks) {
        this.topupAmountKopecks = topupAmountKopecks;
    }

    public String getProviderPaymentMethodId() {
        return providerPaymentMethodId;
    }

    public void setProviderPaymentMethodId(String providerPaymentMethodId) {
        this.providerPaymentMethodId = providerPaymentMethodId;
    }

    public String getCardMask() {
        return cardMask;
    }

    public void setCardMask(String cardMask) {
        this.cardMask = cardMask;
    }

    public Instant getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public void setLastTriggeredAt(Instant lastTriggeredAt) {
        this.lastTriggeredAt = lastTriggeredAt;
    }

    public UUID getLastPaymentId() {
        return lastPaymentId;
    }

    public void setLastPaymentId(UUID lastPaymentId) {
        this.lastPaymentId = lastPaymentId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
