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
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.web.dto.billing.BillingDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.support.ApiStub;

@RestController
@Validated
@RequestMapping(path = "/api/v1/billing", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Billing", description = "Plans, subscription, checkout, invoices, promo codes")
@OpenApiResponses
public class BillingController {

    @GetMapping("/plans")
    @Operation(operationId = "billingListPlans", summary = "List public billing plans (US-6.1)")
    @ApiResponse(
            responseCode = "200",
            description = "Plans",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = BillingDtos.Plan.class))))
    public List<BillingDtos.Plan> listPlans() {
        throw ApiStub.notImplemented("billingListPlans");
    }

    @GetMapping("/subscription")
    @Operation(operationId = "billingGetSubscription", summary = "Get current workspace subscription")
    @ApiResponse(
            responseCode = "200",
            description = "Subscription",
            content = @Content(schema = @Schema(implementation = BillingDtos.Subscription.class)))
    public BillingDtos.Subscription getSubscription(@RequestParam UUID workspaceId) {
        throw ApiStub.notImplemented("billingGetSubscription");
    }

    @PostMapping("/checkout")
    @Operation(operationId = "billingCheckout", summary = "Create YooKassa checkout session (US-6.3)")
    @ApiResponse(
            responseCode = "200",
            description = "Checkout session",
            content = @Content(schema = @Schema(implementation = BillingDtos.CheckoutResponse.class)))
    public BillingDtos.CheckoutResponse checkout(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BillingDtos.CheckoutRequest request) {
        throw ApiStub.notImplemented("billingCheckout");
    }

    @GetMapping("/invoices")
    @Operation(operationId = "billingListInvoices", summary = "List invoices for a workspace")
    @ApiResponse(responseCode = "200", description = "Invoices page")
    public CursorPage.Response<BillingDtos.Invoice> listInvoices(
            @RequestParam UUID workspaceId, @Valid @ParameterObject CursorPage.Params page) {
        throw ApiStub.notImplemented("billingListInvoices");
    }

    @PostMapping("/promo/apply")
    @Operation(operationId = "billingApplyPromo", summary = "Apply a promo code to a workspace (US-6.6)")
    @ApiResponse(
            responseCode = "200",
            description = "Promo applied",
            content = @Content(schema = @Schema(implementation = BillingDtos.ApplyPromoResponse.class)))
    public BillingDtos.ApplyPromoResponse applyPromo(@Valid @RequestBody BillingDtos.ApplyPromoRequest request) {
        throw ApiStub.notImplemented("billingApplyPromo");
    }
}
