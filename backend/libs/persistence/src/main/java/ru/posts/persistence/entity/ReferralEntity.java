package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "referrals")
public class ReferralEntity extends BaseUuidEntity {

    @Column(name = "referrer_user_id")
    private UUID referrerUserId;

    @Column(name = "referred_user_id")
    private UUID referredUserId;

    private String code;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getReferrerUserId() {
        return referrerUserId;
    }

    public void setReferrerUserId(UUID referrerUserId) {
        this.referrerUserId = referrerUserId;
    }

    public UUID getReferredUserId() {
        return referredUserId;
    }

    public void setReferredUserId(UUID referredUserId) {
        this.referredUserId = referredUserId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
