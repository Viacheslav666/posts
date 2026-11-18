package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_charges")
public class DailyChargeEntity extends BaseUuidEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "charge_date", nullable = false)
    private LocalDate chargeDate;

    @Column(name = "amount_kopecks", nullable = false)
    private long amountKopecks;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "channels_counted", nullable = false)
    private int channelsCounted;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(nullable = false)
    private String status = "charged";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public LocalDate getChargeDate() {
        return chargeDate;
    }

    public void setChargeDate(LocalDate chargeDate) {
        this.chargeDate = chargeDate;
    }

    public long getAmountKopecks() {
        return amountKopecks;
    }

    public void setAmountKopecks(long amountKopecks) {
        this.amountKopecks = amountKopecks;
    }

    public UUID getPlanId() {
        return planId;
    }

    public void setPlanId(UUID planId) {
        this.planId = planId;
    }

    public int getChannelsCounted() {
        return channelsCounted;
    }

    public void setChannelsCounted(int channelsCounted) {
        this.channelsCounted = channelsCounted;
    }

    public UUID getLedgerEntryId() {
        return ledgerEntryId;
    }

    public void setLedgerEntryId(UUID ledgerEntryId) {
        this.ledgerEntryId = ledgerEntryId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
