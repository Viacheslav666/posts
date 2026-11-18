package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_telegram_links")
public class NotificationTelegramLinkEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "chat_id")
    private Long chatId;

    @Column
    private String username;

    @Column(name = "link_code_hash")
    private String linkCodeHash;

    @Column(name = "link_code_expires_at")
    private Instant linkCodeExpiresAt;

    @Column(name = "linked_at")
    private Instant linkedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getLinkCodeHash() {
        return linkCodeHash;
    }

    public void setLinkCodeHash(String linkCodeHash) {
        this.linkCodeHash = linkCodeHash;
    }

    public Instant getLinkCodeExpiresAt() {
        return linkCodeExpiresAt;
    }

    public void setLinkCodeExpiresAt(Instant linkCodeExpiresAt) {
        this.linkCodeExpiresAt = linkCodeExpiresAt;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(Instant linkedAt) {
        this.linkedAt = linkedAt;
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

    public boolean isLinked() {
        return chatId != null && linkedAt != null;
    }
}
