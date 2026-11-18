package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grabber_runs")
public class GrabberRunEntity extends BaseUuidEntity {

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String trigger;

    @Column(nullable = false)
    private String status = "running";

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "sources_scanned", nullable = false)
    private int sourcesScanned;

    @Column(name = "items_found", nullable = false)
    private int itemsFound;

    @Column(name = "items_new", nullable = false)
    private int itemsNew;

    @Column(name = "items_deduped", nullable = false)
    private int itemsDeduped;

    @Column(name = "items_published", nullable = false)
    private int itemsPublished;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "trace_id")
    private String traceId;

    public UUID getRuleId() {
        return ruleId;
    }

    public void setRuleId(UUID ruleId) {
        this.ruleId = ruleId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public int getSourcesScanned() {
        return sourcesScanned;
    }

    public void setSourcesScanned(int sourcesScanned) {
        this.sourcesScanned = sourcesScanned;
    }

    public int getItemsFound() {
        return itemsFound;
    }

    public void setItemsFound(int itemsFound) {
        this.itemsFound = itemsFound;
    }

    public int getItemsNew() {
        return itemsNew;
    }

    public void setItemsNew(int itemsNew) {
        this.itemsNew = itemsNew;
    }

    public int getItemsDeduped() {
        return itemsDeduped;
    }

    public void setItemsDeduped(int itemsDeduped) {
        this.itemsDeduped = itemsDeduped;
    }

    public int getItemsPublished() {
        return itemsPublished;
    }

    public void setItemsPublished(int itemsPublished) {
        this.itemsPublished = itemsPublished;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
