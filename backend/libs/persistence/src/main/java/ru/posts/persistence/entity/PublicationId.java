package ru.posts.persistence.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Composite primary key for partitioned {@link PublicationEntity}. */
public class PublicationId implements Serializable {

    private UUID id;
    private Instant runAt;

    public PublicationId() {}

    public PublicationId(UUID id, Instant runAt) {
        this.id = id;
        this.runAt = runAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Instant getRunAt() {
        return runAt;
    }

    public void setRunAt(Instant runAt) {
        this.runAt = runAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PublicationId that)) {
            return false;
        }
        return Objects.equals(id, that.id) && Objects.equals(runAt, that.runAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, runAt);
    }
}
