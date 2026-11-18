package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "invoices")
public class InvoiceEntity extends BaseUuidEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "amount_kopecks", nullable = false)
    private long amountKopecks;

    @Column(nullable = false)
    private String currency = "RUB";

    @Column(nullable = false)
    private String status;

    @Column(name = "pdf_url")
    private String pdfUrl;

    @Column(name = "act_pdf_url")
    private String actPdfUrl;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "buyer_requisites", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> buyerRequisites = Map.of();

    @Column(name = "paid_at")
    private Instant paidAt;

    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public long getAmountKopecks() {
        return amountKopecks;
    }

    public void setAmountKopecks(long amountKopecks) {
        this.amountKopecks = amountKopecks;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPdfUrl() {
        return pdfUrl;
    }

    public void setPdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
    }

    public String getActPdfUrl() {
        return actPdfUrl;
    }

    public void setActPdfUrl(String actPdfUrl) {
        this.actPdfUrl = actPdfUrl;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public Map<String, Object> getBuyerRequisites() {
        return buyerRequisites;
    }

    public void setBuyerRequisites(Map<String, Object> buyerRequisites) {
        this.buyerRequisites = buyerRequisites;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
}
