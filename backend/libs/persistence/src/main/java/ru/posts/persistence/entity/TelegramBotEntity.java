package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "telegram_bots")
public class TelegramBotEntity extends BaseUuidEntity {

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "token_enc", nullable = false)
    private byte[] tokenEnc;

    @Column(name = "webhook_secret", nullable = false)
    private String webhookSecret;

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public byte[] getTokenEnc() {
        return tokenEnc;
    }

    public void setTokenEnc(byte[] tokenEnc) {
        this.tokenEnc = tokenEnc;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
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
