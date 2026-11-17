package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "publication_attempts")
public class PublicationAttemptEntity extends BaseUuidEntity {

    @Column(name = "publication_id", nullable = false)
    private UUID publicationId;

    @Column(name = "publication_run_at", nullable = false)
    private Instant publicationRunAt;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_code")
    private String errorCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_excerpt", columnDefinition = "jsonb")
    private Map<String, Object> responseExcerpt;

    public UUID getPublicationId() {
        return publicationId;
    }

    public void setPublicationId(UUID publicationId) {
        this.publicationId = publicationId;
    }

    public Instant getPublicationRunAt() {
        return publicationRunAt;
    }

    public void setPublicationRunAt(Instant publicationRunAt) {
        this.publicationRunAt = publicationRunAt;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(int attemptNo) {
        this.attemptNo = attemptNo;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Map<String, Object> getResponseExcerpt() {
        return responseExcerpt;
    }

    public void setResponseExcerpt(Map<String, Object> responseExcerpt) {
        this.responseExcerpt = responseExcerpt;
    }
}
