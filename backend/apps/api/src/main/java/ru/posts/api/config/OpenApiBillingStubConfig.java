package ru.posts.api.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.billing.BillingService;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.web.dto.billing.BillingDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;

@Configuration
@Profile("openapi")
public class OpenApiBillingStubConfig {

    @Bean
    BillingService billingService() {
        return new BillingService() {
            @Override
            public List<BillingDtos.Plan> listPlans() {
                return List.of(new BillingDtos.Plan(
                        UUID.fromString("019a0000-0001-7000-8000-000000000103"),
                        "daily_25",
                        "До 25 каналов",
                        new BigDecimal("810.00"),
                        BigDecimal.ZERO,
                        2700L,
                        new BigDecimal("810.00"),
                        "balance",
                        Map.of("channels", 25, "seats", 3)));
            }

            @Override
            public BillingDtos.Subscription getSubscription(UUID userId, UUID workspaceId) {
                return new BillingDtos.Subscription(
                        UUID.randomUUID(),
                        workspaceId,
                        "daily_25",
                        ApiEnums.BillingStatus.active,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-02-01T00:00:00Z"),
                        false,
                        null,
                        "balance");
            }

            @Override
            public BillingDtos.Wallet getWallet(UUID userId, UUID workspaceId) {
                return new BillingDtos.Wallet(
                        workspaceId, 50_000L, 0L, 50_000L, 18, 2700L, "daily_25", ApiEnums.BillingStatus.active);
            }

            @Override
            public BillingDtos.CheckoutResponse checkout(
                    UUID userId, BillingDtos.CheckoutRequest request, String idempotencyKey) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public CursorPage.InvoicePage listInvoices(UUID userId, UUID workspaceId, CursorPage.Params page) {
                return new CursorPage.InvoicePage(List.of(), null, false);
            }

            @Override
            public BillingDtos.Invoice requestInvoice(
                    UUID userId, BillingDtos.RequestInvoiceRequest request, String idempotencyKey) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public byte[] invoicePdf(UUID userId, UUID invoiceId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public byte[] invoiceActPdf(UUID userId, UUID invoiceId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public BillingDtos.ApplyPromoResponse applyPromo(UUID userId, BillingDtos.ApplyPromoRequest request) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public BillingDtos.Subscription startTrial(
                    UUID userId, BillingDtos.StartTrialRequest request, String idempotencyKey) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public CursorPage.TransactionPage listTransactions(
                    UUID userId, UUID workspaceId, String kind, CursorPage.Params page) {
                return new CursorPage.TransactionPage(List.of(), null, false);
            }

            @Override
            public CursorPage.PaymentPage listPayments(UUID userId, UUID workspaceId, CursorPage.Params page) {
                return new CursorPage.PaymentPage(List.of(), null, false);
            }

            @Override
            public BillingDtos.AutopaySettings getAutopay(UUID userId, UUID workspaceId) {
                return new BillingDtos.AutopaySettings(workspaceId, false, 10_000L, 50_000L, null, null);
            }

            @Override
            public BillingDtos.AutopaySettings updateAutopay(UUID userId, BillingDtos.UpdateAutopayRequest request) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public BillingDtos.PaymentMethod getPaymentMethod(UUID userId, UUID workspaceId) {
                return null;
            }

            @Override
            public void deletePaymentMethod(UUID userId, UUID workspaceId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public BillingDtos.Subscription changePlan(UUID userId, BillingDtos.ChangePlanRequest request) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public BillingDtos.AutopaySettings setAutoRenew(UUID userId, BillingDtos.SetAutoRenewRequest request) {
                throw new NotImplementedException("openapi");
            }
        };
    }
}
