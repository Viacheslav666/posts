package ru.posts.api.web.dto.billing;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
            @Schema(example = "pro") String code,
            @Schema(example = "Про") String name,
            BigDecimal priceMonth,
            BigDecimal priceYear,
            @Schema(example = "{\"channels\":30,\"seats\":3,\"posts_month\":null,\"ai_credits\":1000}")
                    Map<String, Object> limits) {}

    @Schema(name = "Subscription")
    public record Subscription(
            UUID id,
            UUID workspaceId,
            String planCode,
            ApiEnums.BillingStatus status,
            Instant periodStart,
            Instant periodEnd,
            boolean cancelAtPeriodEnd,
            Instant trialEndsAt) {}

    @Schema(name = "CheckoutRequest")
    public record CheckoutRequest(
            @NotNull UUID workspaceId,
            @NotBlank @Schema(example = "pro") String planCode,
            @NotNull ApiEnums.BillingPeriod period,
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
            @Schema(example = "RUB") String currency,
            String status,
            String pdfUrl,
            Instant createdAt) {}

    @Schema(name = "ApplyPromoRequest")
    public record ApplyPromoRequest(@NotNull UUID workspaceId, @NotBlank String code) {}

    @Schema(name = "ApplyPromoResponse")
    public record ApplyPromoResponse(
            String code, @Schema(example = "percent") String kind, BigDecimal value, Instant validUntil) {}

    @Schema(name = "StartTrialRequest")
    public record StartTrialRequest(@NotNull UUID workspaceId) {}
}
