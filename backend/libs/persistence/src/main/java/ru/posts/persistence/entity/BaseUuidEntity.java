package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.util.UUID;
import ru.posts.common.uuid.UuidV7;

@MappedSuperclass
public abstract class BaseUuidEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UuidV7.generate();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
