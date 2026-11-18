package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.admin.AdminService;
import ru.posts.api.web.dto.admin.AdminDtos;
import ru.posts.api.web.dto.billing.BillingDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.security.auth.PostsPrincipal;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@Validated
@RequestMapping(path = "/api/v1/admin", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
        name = "Admin",
        description = "Platform admin API (E7 / A13). Requires users.platform_admin — not a workspace role.")
@OpenApiResponses
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    @Operation(operationId = "adminUsersSearch", summary = "Search users (platform admin)")
    @ApiResponse(responseCode = "200", description = "Users page")
    public CursorPage.AdminUserPage searchUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean platformAdmin,
            @Valid @ParameterObject CursorPage.Params page) {
        return adminService.searchUsers(actor().userId(), q, platformAdmin, page);
    }

    @GetMapping("/users/{userId}")
    @Operation(operationId = "adminUsersGet", summary = "User card with workspaces")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = AdminDtos.UserCard.class)))
    public AdminDtos.UserCard getUser(@PathVariable UUID userId) {
        return adminService.getUser(actor().userId(), userId);
    }

    @PostMapping("/users/{userId}/impersonate")
    @Operation(
            operationId = "adminUsersImpersonate",
            summary = "Issue short-lived impersonation access token",
            description =
                    "Records immutable admin audit (who/whom/when/reason). Token has claim imp=true for FE banner. "
                            + "Password/payment mutations are forbidden while impersonating.")
    @ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = AdminDtos.ImpersonateResponse.class)))
    public AdminDtos.ImpersonateResponse impersonate(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminDtos.ImpersonateRequest request,
            HttpServletRequest httpRequest) {
        PostsPrincipal principal = actor();
        return adminService.impersonate(
                principal.userId(), userId, request, principal.sessionId(), clientIp(httpRequest));
    }

    @GetMapping("/workspaces")
    @Operation(operationId = "adminWorkspacesSearch", summary = "Search workspaces")
    @ApiResponse(responseCode = "200", description = "Workspaces page")
    public CursorPage.AdminWorkspacePage searchWorkspaces(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String billingStatus,
            @RequestParam(required = false) String planCode,
            @Valid @ParameterObject CursorPage.Params page) {
        return adminService.searchWorkspaces(actor().userId(), q, billingStatus, planCode, page);
    }

    @GetMapping("/workspaces/{workspaceId}")
    @Operation(operationId = "adminWorkspacesGet", summary = "Workspace card: plan, limits, channels, recent errors")
    @ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = AdminDtos.WorkspaceCard.class)))
    public AdminDtos.WorkspaceCard getWorkspace(@PathVariable UUID workspaceId) {
        return adminService.getWorkspace(actor().userId(), workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/subscription")
    @Operation(operationId = "adminWorkspacesAdjustSubscription", summary = "Manually adjust subscription/plan")
    @ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = AdminDtos.WorkspaceCard.class)))
    public AdminDtos.WorkspaceCard adjustSubscription(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody AdminDtos.AdjustSubscriptionRequest request,
            HttpServletRequest httpRequest) {
        return adminService.adjustSubscription(actor().userId(), workspaceId, request, clientIp(httpRequest));
    }

    @PostMapping("/workspaces/{workspaceId}/balance")
    @Operation(operationId = "adminWorkspacesAdjustBalance", summary = "Manual ledger balance adjustment")
    @ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = AdminDtos.LedgerEntry.class)))
    public AdminDtos.LedgerEntry adjustBalance(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody AdminDtos.AdjustBalanceRequest request,
            HttpServletRequest httpRequest) {
        return adminService.adjustBalance(actor().userId(), workspaceId, request, clientIp(httpRequest));
    }

    @GetMapping("/plans")
    @Operation(operationId = "adminPlansList", summary = "List billing plans (admin)")
    @ApiResponse(
            responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = BillingDtos.Plan.class))))
    public List<BillingDtos.Plan> listPlans() {
        return adminService.listPlans(actor().userId());
    }

    @PutMapping("/plans")
    @Operation(operationId = "adminPlansUpsert", summary = "Create or update a billing plan")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = BillingDtos.Plan.class)))
    public BillingDtos.Plan upsertPlan(
            @Valid @RequestBody AdminDtos.PlanUpsertRequest request, HttpServletRequest httpRequest) {
        return adminService.upsertPlan(actor().userId(), request, clientIp(httpRequest));
    }

    @GetMapping("/promos")
    @Operation(operationId = "adminPromosList", summary = "List promo codes")
    @ApiResponse(responseCode = "200", description = "Promo codes page")
    public CursorPage.AdminPromoPage listPromos(@Valid @ParameterObject CursorPage.Params page) {
        return adminService.listPromos(actor().userId(), page);
    }

    @PostMapping("/promos")
    @Operation(operationId = "adminPromosCreate", summary = "Create promo code")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = AdminDtos.PromoCode.class)))
    public AdminDtos.PromoCode createPromo(
            @Valid @RequestBody AdminDtos.PromoCreateRequest request, HttpServletRequest httpRequest) {
        return adminService.createPromo(actor().userId(), request, clientIp(httpRequest));
    }

    @PutMapping("/promos/{promoId}")
    @Operation(operationId = "adminPromosUpdate", summary = "Update promo code")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = AdminDtos.PromoCode.class)))
    public AdminDtos.PromoCode updatePromo(
            @PathVariable UUID promoId,
            @Valid @RequestBody AdminDtos.PromoUpdateRequest request,
            HttpServletRequest httpRequest) {
        return adminService.updatePromo(actor().userId(), promoId, request, clientIp(httpRequest));
    }

    @GetMapping("/queues")
    @Operation(operationId = "adminQueuesList", summary = "List publish/action/media queues and DLQs")
    @ApiResponse(
            responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdminDtos.QueueInfo.class))))
    public List<AdminDtos.QueueInfo> listQueues() {
        return adminService.listQueues(actor().userId());
    }

    @GetMapping("/queues/{queue}/messages")
    @Operation(operationId = "adminQueuesListMessages", summary = "Peek queue / DLQ messages")
    @ApiResponse(responseCode = "200", description = "Messages page")
    public CursorPage.AdminQueueMessagePage listQueueMessages(
            @PathVariable String queue, @Valid @ParameterObject CursorPage.Params page) {
        return adminService.listQueueMessages(actor().userId(), queue, page);
    }

    @PostMapping("/queues/{queue}/messages/{messageId}/retry")
    @Operation(
            operationId = "adminQueuesRetryMessage",
            summary = "Retry (requeue) a DLQ message; Idempotency-Key required")
    @ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = AdminDtos.QueueMessageActionResponse.class)))
    public AdminDtos.QueueMessageActionResponse retryQueueMessage(
            @PathVariable String queue,
            @PathVariable String messageId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        return adminService.retryQueueMessage(
                actor().userId(), queue, messageId, idempotencyKey, clientIp(httpRequest));
    }

    @PostMapping("/queues/{queue}/messages/{messageId}/delete")
    @Operation(operationId = "adminQueuesDeleteMessage", summary = "Delete a DLQ message; Idempotency-Key required")
    @ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = AdminDtos.QueueMessageActionResponse.class)))
    public AdminDtos.QueueMessageActionResponse deleteQueueMessage(
            @PathVariable String queue,
            @PathVariable String messageId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        return adminService.deleteQueueMessage(
                actor().userId(), queue, messageId, idempotencyKey, clientIp(httpRequest));
    }

    @GetMapping("/feature-flags")
    @Operation(operationId = "adminFeatureFlagsList", summary = "List feature flags")
    @ApiResponse(
            responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdminDtos.FeatureFlag.class))))
    public List<AdminDtos.FeatureFlag> listFeatureFlags() {
        return adminService.listFeatureFlags(actor().userId());
    }

    @PutMapping("/feature-flags")
    @Operation(operationId = "adminFeatureFlagsUpsert", summary = "Create or update a global feature flag")
    @ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = AdminDtos.FeatureFlag.class)))
    public AdminDtos.FeatureFlag upsertFeatureFlag(
            @Valid @RequestBody AdminDtos.FeatureFlagUpsertRequest request, HttpServletRequest httpRequest) {
        return adminService.upsertFeatureFlag(actor().userId(), request, clientIp(httpRequest));
    }

    @PutMapping("/workspaces/{workspaceId}/feature-flags/{flagKey}")
    @Operation(operationId = "adminWorkspaceFeatureFlagsSet", summary = "Override feature flag for a workspace")
    @ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = AdminDtos.WorkspaceFeatureFlag.class)))
    public AdminDtos.WorkspaceFeatureFlag setWorkspaceFeatureFlag(
            @PathVariable UUID workspaceId,
            @PathVariable String flagKey,
            @Valid @RequestBody AdminDtos.WorkspaceFeatureFlagRequest request,
            HttpServletRequest httpRequest) {
        return adminService.setWorkspaceFeatureFlag(
                actor().userId(), workspaceId, flagKey, request, clientIp(httpRequest));
    }

    @GetMapping("/announcements")
    @Operation(operationId = "adminAnnouncementsList", summary = "List announcements / banners")
    @ApiResponse(responseCode = "200", description = "Announcements page")
    public CursorPage.AdminAnnouncementPage listAnnouncements(@Valid @ParameterObject CursorPage.Params page) {
        return adminService.listAnnouncements(actor().userId(), page);
    }

    @PostMapping("/announcements")
    @Operation(operationId = "adminAnnouncementsCreate", summary = "Create announcement banner")
    @ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = AdminDtos.Announcement.class)))
    public AdminDtos.Announcement createAnnouncement(
            @Valid @RequestBody AdminDtos.AnnouncementCreateRequest request, HttpServletRequest httpRequest) {
        return adminService.createAnnouncement(actor().userId(), request, clientIp(httpRequest));
    }

    @PutMapping("/announcements/{announcementId}")
    @Operation(operationId = "adminAnnouncementsUpdate", summary = "Update announcement banner")
    @ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = AdminDtos.Announcement.class)))
    public AdminDtos.Announcement updateAnnouncement(
            @PathVariable UUID announcementId,
            @Valid @RequestBody AdminDtos.AnnouncementUpdateRequest request,
            HttpServletRequest httpRequest) {
        return adminService.updateAnnouncement(actor().userId(), announcementId, request, clientIp(httpRequest));
    }

    @GetMapping("/audit")
    @Operation(operationId = "adminAuditList", summary = "Immutable platform-admin audit log")
    @ApiResponse(responseCode = "200", description = "Admin audit page")
    public CursorPage.AdminAuditPage listAudit(
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID targetId,
            @Valid @ParameterObject CursorPage.Params page) {
        return adminService.listAudit(actor().userId(), actorUserId, action, targetType, targetId, page);
    }

    private static PostsPrincipal actor() {
        return SecurityAuthSupport.requirePrincipal();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded;
        }
        return request.getRemoteAddr();
    }
}
