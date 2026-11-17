package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "plans")
public class PlanEntity extends BaseUuidEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_month", nullable = false)
    private BigDecimal priceMonth = BigDecimal.ZERO;

    @Column(name = "price_year", nullable = false)
    private BigDecimal priceYear = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> limits = Map.of();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPriceMonth() {
        return priceMonth;
    }

    public void setPriceMonth(BigDecimal priceMonth) {
        this.priceMonth = priceMonth;
    }

    public BigDecimal getPriceYear() {
        return priceYear;
    }

    public void setPriceYear(BigDecimal priceYear) {
        this.priceYear = priceYear;
    }

    public Map<String, Object> getLimits() {
        return limits;
    }

    public void setLimits(Map<String, Object> limits) {
        this.limits = limits;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
