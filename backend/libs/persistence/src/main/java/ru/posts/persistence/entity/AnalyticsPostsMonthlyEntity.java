package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Materialised monthly analytics row (BE-30). Written by the aggregator job only. */
@Entity
@Table(name = "analytics_posts_monthly")
@IdClass(AnalyticsPostsMonthlyEntity.Pk.class)
public class AnalyticsPostsMonthlyEntity {

    @Id
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Id
    @Column(nullable = false)
    private int year;

    @Id
    @Column(nullable = false)
    private int month;

    @Column(name = "regular_count", nullable = false)
    private long regularCount;

    @Column(name = "ad_count", nullable = false)
    private long adCount;

    @Column(name = "repost_count", nullable = false)
    private long repostCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "network_succeeded", nullable = false, columnDefinition = "jsonb")
    private Map<String, Long> networkSucceeded = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "network_failed", nullable = false, columnDefinition = "jsonb")
    private Map<String, Long> networkFailed = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_errors", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> topErrors = List.of();

    @Column(name = "revenue_with_vat", nullable = false)
    private BigDecimal revenueWithVat = BigDecimal.ZERO;

    @Column(name = "revenue_without_vat", nullable = false)
    private BigDecimal revenueWithoutVat = BigDecimal.ZERO;

    @Column(name = "revenue_vat", nullable = false)
    private BigDecimal revenueVat = BigDecimal.ZERO;

    @Column(name = "revenue_fee_3pct", nullable = false)
    private BigDecimal revenueFee3Pct = BigDecimal.ZERO;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt = Instant.now();

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public long getRegularCount() {
        return regularCount;
    }

    public void setRegularCount(long regularCount) {
        this.regularCount = regularCount;
    }

    public long getAdCount() {
        return adCount;
    }

    public void setAdCount(long adCount) {
        this.adCount = adCount;
    }

    public long getRepostCount() {
        return repostCount;
    }

    public void setRepostCount(long repostCount) {
        this.repostCount = repostCount;
    }

    public Map<String, Long> getNetworkSucceeded() {
        return networkSucceeded;
    }

    public void setNetworkSucceeded(Map<String, Long> networkSucceeded) {
        this.networkSucceeded = networkSucceeded == null ? Map.of() : networkSucceeded;
    }

    public Map<String, Long> getNetworkFailed() {
        return networkFailed;
    }

    public void setNetworkFailed(Map<String, Long> networkFailed) {
        this.networkFailed = networkFailed == null ? Map.of() : networkFailed;
    }

    public List<Map<String, Object>> getTopErrors() {
        return topErrors;
    }

    public void setTopErrors(List<Map<String, Object>> topErrors) {
        this.topErrors = topErrors == null ? List.of() : topErrors;
    }

    public BigDecimal getRevenueWithVat() {
        return revenueWithVat;
    }

    public void setRevenueWithVat(BigDecimal revenueWithVat) {
        this.revenueWithVat = revenueWithVat;
    }

    public BigDecimal getRevenueWithoutVat() {
        return revenueWithoutVat;
    }

    public void setRevenueWithoutVat(BigDecimal revenueWithoutVat) {
        this.revenueWithoutVat = revenueWithoutVat;
    }

    public BigDecimal getRevenueVat() {
        return revenueVat;
    }

    public void setRevenueVat(BigDecimal revenueVat) {
        this.revenueVat = revenueVat;
    }

    public BigDecimal getRevenueFee3Pct() {
        return revenueFee3Pct;
    }

    public void setRevenueFee3Pct(BigDecimal revenueFee3Pct) {
        this.revenueFee3Pct = revenueFee3Pct;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }

    public static final class Pk implements Serializable {
        private UUID workspaceId;
        private int year;
        private int month;

        public Pk() {}

        public Pk(UUID workspaceId, int year, int month) {
            this.workspaceId = workspaceId;
            this.year = year;
            this.month = month;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk pk)) {
                return false;
            }
            return year == pk.year && month == pk.month && Objects.equals(workspaceId, pk.workspaceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(workspaceId, year, month);
        }
    }
}
