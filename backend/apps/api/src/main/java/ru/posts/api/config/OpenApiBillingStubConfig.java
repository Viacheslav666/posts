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
                        UUID.fromString("019a0000-0001-7000-8000-000000000003"),
                        "pro",
                        "Про",
                        new BigDecimal("990.00"),
                        new BigDecimal("9504.00"),
                        Map.of("channels", 30, "seats", 3)));
            }

            @Override
            public BillingDtos.Subscription getSubscription(UUID userId, UUID workspaceId) {
                return new BillingDtos.Subscription(
                        UUID.randomUUID(),
                        workspaceId,
                        "pro",
                        ApiEnums.BillingStatus.active,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-02-01T00:00:00Z"),
                        false,
                        null);
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
            public BillingDtos.ApplyPromoResponse applyPromo(UUID userId, BillingDtos.ApplyPromoRequest request) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public BillingDtos.Subscription startTrial(
                    UUID userId, BillingDtos.StartTrialRequest request, String idempotencyKey) {
                throw new NotImplementedException("openapi");
            }
        };
    }
}
