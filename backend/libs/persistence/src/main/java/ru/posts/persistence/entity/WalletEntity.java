package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class WalletEntity {

    @Id
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "balance_kopecks", nullable = false)
    private long balanceKopecks;

    @Column(name = "bonus_balance_kopecks", nullable = false)
    private long bonusBalanceKopecks;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public long getBalanceKopecks() {
        return balanceKopecks;
    }

    public void setBalanceKopecks(long balanceKopecks) {
        this.balanceKopecks = balanceKopecks;
    }

    public long getBonusBalanceKopecks() {
        return bonusBalanceKopecks;
    }

    public void setBonusBalanceKopecks(long bonusBalanceKopecks) {
        this.bonusBalanceKopecks = bonusBalanceKopecks;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** Real funds available for refund = balance − bonus. */
    public long refundableKopecks() {
        return Math.max(0L, balanceKopecks - bonusBalanceKopecks);
    }
}
