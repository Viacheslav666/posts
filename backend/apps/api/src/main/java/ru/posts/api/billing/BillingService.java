package ru.posts.api.billing;

import java.util.List;
import java.util.UUID;
import ru.posts.api.web.dto.billing.BillingDtos;
import ru.posts.api.web.dto.common.CursorPage;

public interface BillingService {

    List<BillingDtos.Plan> listPlans();

    BillingDtos.Subscription getSubscription(UUID userId, UUID workspaceId);

    BillingDtos.Wallet getWallet(UUID userId, UUID workspaceId);

    BillingDtos.CheckoutResponse checkout(UUID userId, BillingDtos.CheckoutRequest request, String idempotencyKey);

    CursorPage.InvoicePage listInvoices(UUID userId, UUID workspaceId, CursorPage.Params page);

    BillingDtos.Invoice requestInvoice(UUID userId, BillingDtos.RequestInvoiceRequest request, String idempotencyKey);

    byte[] invoicePdf(UUID userId, UUID invoiceId);

    byte[] invoiceActPdf(UUID userId, UUID invoiceId);

    BillingDtos.ApplyPromoResponse applyPromo(UUID userId, BillingDtos.ApplyPromoRequest request);

    BillingDtos.Subscription startTrial(UUID userId, BillingDtos.StartTrialRequest request, String idempotencyKey);

    CursorPage.TransactionPage listTransactions(UUID userId, UUID workspaceId, String kind, CursorPage.Params page);

    CursorPage.PaymentPage listPayments(UUID userId, UUID workspaceId, CursorPage.Params page);

    BillingDtos.AutopaySettings getAutopay(UUID userId, UUID workspaceId);

    BillingDtos.AutopaySettings updateAutopay(UUID userId, BillingDtos.UpdateAutopayRequest request);

    BillingDtos.PaymentMethod getPaymentMethod(UUID userId, UUID workspaceId);

    void deletePaymentMethod(UUID userId, UUID workspaceId);

    BillingDtos.Subscription changePlan(UUID userId, BillingDtos.ChangePlanRequest request);

    BillingDtos.AutopaySettings setAutoRenew(UUID userId, BillingDtos.SetAutoRenewRequest request);
}
