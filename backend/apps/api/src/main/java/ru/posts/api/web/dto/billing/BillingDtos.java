package ru.posts.api.web.dto.billing;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import ru.posts.api.web.dto.common.ApiEnums;

public final class BillingDtos {

    private BillingDtos() {}

    @Schema(name = "Plan")
    public record Plan(
            UUID id,
            @Schema(example = "daily_25") String code,
            @Schema(example = "До 25 каналов") String name,
            BigDecimal priceMonth,
            BigDecimal priceYear,
            @Schema(description = "Daily price in kopecks", example = "2700") long priceDayKopecks,
            @Schema(description = "Display monthly price ≈ day×30", example = "810.00") BigDecimal priceMonthDisplay,
            @Schema(example = "balance") String billingModel,
            @Schema(example = "{\"channels\":25,\"seats\":3}") Map<String, Object> limits) {}

    @Schema(name = "Subscription", description = "Current plan + billing status (legacy-compatible shape)")
    public record Subscription(
            UUID id,
            UUID workspaceId,
            String planCode,
            ApiEnums.BillingStatus status,
            Instant periodStart,
            Instant periodEnd,
            boolean cancelAtPeriodEnd,
            Instant trialEndsAt,
            @Schema(example = "balance") String billingModel) {}

    @Schema(name = "Wallet")
    public record Wallet(
            UUID workspaceId,
            @Schema(description = "Balance in kopecks") long balanceKopecks,
            @Schema(description = "Bonus portion in kopecks") long bonusBalanceKopecks,
            @Schema(description = "Refundable = balance − bonus") long refundableKopecks,
            @Schema(description = "Forecast days remaining at current daily price") Integer daysRemaining,
            long priceDayKopecks,
            String planCode,
            ApiEnums.BillingStatus billingStatus) {}

    @Schema(name = "CheckoutRequest")
    public record CheckoutRequest(
            @NotNull UUID workspaceId,
            @Schema(description = "Top-up amount in kopecks (balance mode)", example = "200000") @Min(100)
                    Long amountKopecks,
            @Schema(description = "Legacy subscription plan code") String planCode,
            ApiEnums.BillingPeriod period,
            String promoCode) {}

    @Schema(name = "CheckoutResponse")
    public record CheckoutResponse(
            UUID paymentId,
            @Schema(example = "https://yookassa.ru/checkout/...") String confirmationUrl,
            String provider) {}

    @Schema(name = "Invoice")
    public record Invoice(
            UUID id,
            UUID workspaceId,
            BigDecimal amount,
            long amountKopecks,
            @Schema(example = "RUB") String currency,
            String status,
            String invoiceNumber,
            String pdfUrl,
            String actPdfUrl,
            Map<String, Object> buyerRequisites,
            Instant paidAt,
            Instant createdAt) {}

    @Schema(name = "ApplyPromoRequest")
    public record ApplyPromoRequest(@NotNull UUID workspaceId, @NotBlank String code) {}

    @Schema(name = "ApplyPromoResponse")
    public record ApplyPromoResponse(
            String code,
            @Schema(example = "fixed") String kind,
            BigDecimal value,
            Instant validUntil,
            @Schema(description = "Bonus credited in kopecks when kind credits balance") Long creditedKopecks) {}

    @Schema(name = "StartTrialRequest")
    public record StartTrialRequest(@NotNull UUID workspaceId) {}

    @Schema(name = "Transaction")
    public record Transaction(
            UUID id,
            Instant createdAt,
            @Schema(description = "Signed amount in kopecks (credit >, debit <)") long amountKopecks,
            @Schema(example = "payment") String kind,
            boolean bonus,
            @Schema(description = "Payment commission in kopecks if known") Long commissionKopecks,
            String method,
            String receiptUrl,
            String actUrl,
            String details,
            UUID paymentId,
            UUID invoiceId) {}

    @Schema(name = "PaymentMethod")
    public record PaymentMethod(
            String providerPaymentMethodId, @Schema(example = "**** 1234") String cardMask, String type) {}

    @Schema(name = "AutopaySettings")
    public record AutopaySettings(
            UUID workspaceId,
            boolean enabled,
            long thresholdKopecks,
            long topupAmountKopecks,
            PaymentMethod paymentMethod,
            Instant lastTriggeredAt) {}

    @Schema(name = "UpdateAutopayRequest")
    public record UpdateAutopayRequest(
            @NotNull UUID workspaceId,
            @NotNull Boolean enabled,
            @Min(1) Long thresholdKopecks,
            @Min(1) Long topupAmountKopecks) {}

    @Schema(name = "ChangePlanRequest")
    public record ChangePlanRequest(
            @NotNull UUID workspaceId, @NotBlank @Schema(example = "daily_25") String planCode) {}

    @Schema(name = "BuyerRequisites")
    public record BuyerRequisites(
            @NotBlank @Size(max = 256) String name,
            @NotBlank @Pattern(regexp = "\\d{10}|\\d{12}", message = "INN must be 10 or 12 digits") String inn,
            @Pattern(regexp = "\\d{9}", message = "KPP must be 9 digits") String kpp,
            @Pattern(regexp = "\\d{13}|\\d{15}", message = "OGRN must be 13 or 15 digits") String ogrn,
            @Size(max = 512) String address,
            @Size(max = 256) String bankName,
            @Pattern(regexp = "\\d{20}", message = "Settlement account must be 20 digits") String bankAccount,
            @Pattern(regexp = "\\d{9}", message = "BIK must be 9 digits") String bik) {}

    @Schema(name = "RequestInvoiceRequest")
    public record RequestInvoiceRequest(
            @NotNull UUID workspaceId, @NotNull @Min(100) Long amountKopecks, @NotNull @Valid BuyerRequisites buyer) {}

    @Schema(name = "SetAutoRenewRequest", description = "Legacy alias for autopay enable/disable")
    public record SetAutoRenewRequest(@NotNull UUID workspaceId, @NotNull Boolean enabled) {}

    @Schema(name = "PaymentListItem")
    public record PaymentListItem(
            UUID id,
            Instant createdAt,
            long amountKopecks,
            String status,
            String purpose,
            String provider,
            String confirmationUrl,
            String receiptId,
            String method) {}
}
