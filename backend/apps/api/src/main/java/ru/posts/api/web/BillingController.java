package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.billing.BillingService;
import ru.posts.api.web.dto.billing.BillingDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@Validated
@RequestMapping(path = "/api/v1/billing", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Billing", description = "Wallet, plans, top-up, autopay, invoices, promo codes")
@OpenApiResponses
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/plans")
    @Operation(operationId = "billingListPlans", summary = "List public billing plans")
    @ApiResponse(
            responseCode = "200",
            description = "Plans",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = BillingDtos.Plan.class))))
    public List<BillingDtos.Plan> listPlans() {
        SecurityAuthSupport.requirePrincipal();
        return billingService.listPlans();
    }

    @GetMapping("/wallet")
    @Operation(operationId = "billingGetWallet", summary = "Get workspace wallet balance and forecast")
    @ApiResponse(
            responseCode = "200",
            description = "Wallet",
            content = @Content(schema = @Schema(implementation = BillingDtos.Wallet.class)))
    public BillingDtos.Wallet getWallet(@RequestParam UUID workspaceId) {
        return billingService.getWallet(SecurityAuthSupport.requirePrincipal().userId(), workspaceId);
    }

    @GetMapping("/subscription")
    @Operation(operationId = "billingGetSubscription", summary = "Get current workspace plan / billing status")
    @ApiResponse(
            responseCode = "200",
            description = "Subscription",
            content = @Content(schema = @Schema(implementation = BillingDtos.Subscription.class)))
    public BillingDtos.Subscription getSubscription(@RequestParam UUID workspaceId) {
        return billingService.getSubscription(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId);
    }

    @PostMapping("/checkout")
    @Operation(operationId = "billingCheckout", summary = "Create YooKassa top-up or subscription checkout")
    @ApiResponse(
            responseCode = "200",
            description = "Checkout session",
            content = @Content(schema = @Schema(implementation = BillingDtos.CheckoutResponse.class)))
    public BillingDtos.CheckoutResponse checkout(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BillingDtos.CheckoutRequest request) {
        return billingService.checkout(SecurityAuthSupport.requirePrincipal().userId(), request, idempotencyKey);
    }

    @GetMapping("/transactions")
    @Operation(operationId = "billingListTransactions", summary = "Wallet transaction history")
    @ApiResponse(responseCode = "200", description = "Transactions page")
    public CursorPage.TransactionPage listTransactions(
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) String kind,
            @Valid @ParameterObject CursorPage.Params page) {
        return billingService.listTransactions(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, kind, page);
    }

    @GetMapping("/payments")
    @Operation(operationId = "billingListPayments", summary = "Payment history")
    @ApiResponse(responseCode = "200", description = "Payments page")
    public CursorPage.PaymentPage listPayments(
            @RequestParam UUID workspaceId, @Valid @ParameterObject CursorPage.Params page) {
        return billingService.listPayments(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, page);
    }

    @GetMapping("/invoices")
    @Operation(operationId = "billingListInvoices", summary = "List invoices for a workspace")
    @ApiResponse(responseCode = "200", description = "Invoices page")
    public CursorPage.InvoicePage listInvoices(
            @RequestParam UUID workspaceId, @Valid @ParameterObject CursorPage.Params page) {
        return billingService.listInvoices(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, page);
    }

    @PostMapping("/invoices/request")
    @Operation(operationId = "billingRequestInvoice", summary = "Request a legal-entity invoice (PDF)")
    @ApiResponse(
            responseCode = "200",
            description = "Invoice",
            content = @Content(schema = @Schema(implementation = BillingDtos.Invoice.class)))
    public BillingDtos.Invoice requestInvoice(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BillingDtos.RequestInvoiceRequest request) {
        return billingService.requestInvoice(
                SecurityAuthSupport.requirePrincipal().userId(), request, idempotencyKey);
    }

    @GetMapping(path = "/invoices/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(operationId = "billingInvoicePdf", summary = "Download invoice PDF")
    public ResponseEntity<byte[]> invoicePdf(@PathVariable("id") UUID id) {
        byte[] pdf =
                billingService.invoicePdf(SecurityAuthSupport.requirePrincipal().userId(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping(path = "/invoices/{id}/act", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(operationId = "billingInvoiceActPdf", summary = "Download closing act PDF")
    public ResponseEntity<byte[]> invoiceActPdf(@PathVariable("id") UUID id) {
        byte[] pdf = billingService.invoiceActPdf(
                SecurityAuthSupport.requirePrincipal().userId(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"act-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/promo/apply")
    @Operation(operationId = "billingApplyPromo", summary = "Apply a promo code (balance bonus or trial)")
    @ApiResponse(
            responseCode = "200",
            description = "Promo applied",
            content = @Content(schema = @Schema(implementation = BillingDtos.ApplyPromoResponse.class)))
    public BillingDtos.ApplyPromoResponse applyPromo(@Valid @RequestBody BillingDtos.ApplyPromoRequest request) {
        return billingService.applyPromo(SecurityAuthSupport.requirePrincipal().userId(), request);
    }

    @PostMapping("/trial/start")
    @Operation(operationId = "billingStartTrial", summary = "Start trial without a card")
    @ApiResponse(
            responseCode = "200",
            description = "Trial started",
            content = @Content(schema = @Schema(implementation = BillingDtos.Subscription.class)))
    public BillingDtos.Subscription startTrial(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BillingDtos.StartTrialRequest request) {
        return billingService.startTrial(SecurityAuthSupport.requirePrincipal().userId(), request, idempotencyKey);
    }

    @GetMapping("/autopay")
    @Operation(operationId = "billingGetAutopay", summary = "Get autopay settings")
    @ApiResponse(
            responseCode = "200",
            description = "Autopay settings",
            content = @Content(schema = @Schema(implementation = BillingDtos.AutopaySettings.class)))
    public BillingDtos.AutopaySettings getAutopay(@RequestParam UUID workspaceId) {
        return billingService.getAutopay(SecurityAuthSupport.requirePrincipal().userId(), workspaceId);
    }

    @PutMapping("/autopay")
    @Operation(operationId = "billingUpdateAutopay", summary = "Update autopay settings")
    @ApiResponse(
            responseCode = "200",
            description = "Autopay settings",
            content = @Content(schema = @Schema(implementation = BillingDtos.AutopaySettings.class)))
    public BillingDtos.AutopaySettings updateAutopay(@Valid @RequestBody BillingDtos.UpdateAutopayRequest request) {
        return billingService.updateAutopay(
                SecurityAuthSupport.requirePrincipal().userId(), request);
    }

    @PostMapping("/subscription/auto-renew")
    @Operation(operationId = "billingSetAutoRenew", summary = "Enable/disable autopay (legacy alias)")
    @ApiResponse(
            responseCode = "200",
            description = "Autopay settings",
            content = @Content(schema = @Schema(implementation = BillingDtos.AutopaySettings.class)))
    public BillingDtos.AutopaySettings setAutoRenew(@Valid @RequestBody BillingDtos.SetAutoRenewRequest request) {
        return billingService.setAutoRenew(
                SecurityAuthSupport.requirePrincipal().userId(), request);
    }

    @GetMapping("/payment-method")
    @Operation(operationId = "billingGetPaymentMethod", summary = "Get saved payment method mask")
    @ApiResponse(
            responseCode = "200",
            description = "Payment method",
            content = @Content(schema = @Schema(implementation = BillingDtos.PaymentMethod.class)))
    public BillingDtos.PaymentMethod getPaymentMethod(@RequestParam UUID workspaceId) {
        return billingService.getPaymentMethod(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId);
    }

    @DeleteMapping("/payment-method")
    @Operation(operationId = "billingDeletePaymentMethod", summary = "Detach saved payment method")
    @ApiResponse(responseCode = "204", description = "Detached")
    public ResponseEntity<Void> deletePaymentMethod(@RequestParam UUID workspaceId) {
        billingService.deletePaymentMethod(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/plan/change")
    @Operation(operationId = "billingChangePlan", summary = "Change tariff plan immediately")
    @ApiResponse(
            responseCode = "200",
            description = "Updated subscription/plan",
            content = @Content(schema = @Schema(implementation = BillingDtos.Subscription.class)))
    public BillingDtos.Subscription changePlan(@Valid @RequestBody BillingDtos.ChangePlanRequest request) {
        return billingService.changePlan(SecurityAuthSupport.requirePrincipal().userId(), request);
    }
}
