package ru.posts.api.billing;

import java.util.List;
import java.util.UUID;
import ru.posts.api.web.dto.billing.BillingDtos;
import ru.posts.api.web.dto.common.CursorPage;

public interface BillingService {

    List<BillingDtos.Plan> listPlans();

    BillingDtos.Subscription getSubscription(UUID userId, UUID workspaceId);

    BillingDtos.CheckoutResponse checkout(UUID userId, BillingDtos.CheckoutRequest request, String idempotencyKey);

    CursorPage.InvoicePage listInvoices(UUID userId, UUID workspaceId, CursorPage.Params page);

    BillingDtos.ApplyPromoResponse applyPromo(UUID userId, BillingDtos.ApplyPromoRequest request);

    BillingDtos.Subscription startTrial(UUID userId, BillingDtos.StartTrialRequest request, String idempotencyKey);
}
