package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "usage_counters")
@IdClass(UsageCounterEntity.UsageCounterId.class)
public class UsageCounterEntity {

    @Id
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Id
    @Column(nullable = false)
    private String period;

    @Id
    @Column(nullable = false)
    private String metric;

    @Column(nullable = false)
    private long value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static final class UsageCounterId implements Serializable {
        private UUID workspaceId;
        private String period;
        private String metric;

        public UsageCounterId() {}

        public UsageCounterId(UUID workspaceId, String period, String metric) {
            this.workspaceId = workspaceId;
            this.period = period;
            this.metric = metric;
        }

        public UUID getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(UUID workspaceId) {
            this.workspaceId = workspaceId;
        }

        public String getPeriod() {
            return period;
        }

        public void setPeriod(String period) {
            this.period = period;
        }

        public String getMetric() {
            return metric;
        }

        public void setMetric(String metric) {
            this.metric = metric;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof UsageCounterId that)) {
                return false;
            }
            return Objects.equals(workspaceId, that.workspaceId)
                    && Objects.equals(period, that.period)
                    && Objects.equals(metric, that.metric);
        }

        @Override
        public int hashCode() {
            return Objects.hash(workspaceId, period, metric);
        }
    }
}
