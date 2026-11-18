package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.auth.ClientRequestContexts;
import ru.posts.api.ingest.IngestService;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.ingest.IngestDtos;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@Validated
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Ingest", description = "Grabber / RSS / website automation rules, runs, moderation queue (BE-17/BE-17b)")
@OpenApiResponses
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/ingest/rules")
    @Operation(operationId = "ingestRulesList", summary = "List ingest rules in a project")
    @ApiResponse(
            responseCode = "200",
            description = "Rules page",
            content = @Content(schema = @Schema(implementation = CursorPage.GrabberRulePage.class)))
    public CursorPage.GrabberRulePage listRules(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @RequestParam(required = false) IngestDtos.RuleType type,
            @Valid @ParameterObject CursorPage.Params page) {
        return ingestService.listRules(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, projectId, type, page);
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/ingest/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "ingestRulesCreate", summary = "Create an ingest rule")
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(implementation = IngestDtos.GrabberRule.class)))
    public IngestDtos.GrabberRule createRule(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody IngestDtos.GrabberRuleCreate request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        return ingestService.createRule(
                SecurityAuthSupport.requirePrincipal().userId(),
                workspaceId,
                projectId,
                request,
                idempotencyKey,
                ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/api/v1/ingest/rules/{ruleId}")
    @Operation(operationId = "ingestRulesGet", summary = "Get ingest rule")
    @ApiResponse(
            responseCode = "200",
            description = "Rule",
            content = @Content(schema = @Schema(implementation = IngestDtos.GrabberRule.class)))
    public IngestDtos.GrabberRule getRule(@PathVariable UUID ruleId) {
        return ingestService.getRule(SecurityAuthSupport.requirePrincipal().userId(), ruleId);
    }

    @PatchMapping("/api/v1/ingest/rules/{ruleId}")
    @Operation(operationId = "ingestRulesPatch", summary = "Update ingest rule config/name")
    @ApiResponse(
            responseCode = "200",
            description = "Updated rule",
            content = @Content(schema = @Schema(implementation = IngestDtos.GrabberRule.class)))
    public IngestDtos.GrabberRule patchRule(
            @PathVariable UUID ruleId,
            @Valid @RequestBody IngestDtos.GrabberRulePatch request,
            HttpServletRequest httpRequest) {
        return ingestService.patchRule(
                SecurityAuthSupport.requirePrincipal().userId(),
                ruleId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/ingest/rules/{ruleId}/activate")
    @Operation(operationId = "ingestRulesActivate", summary = "Activate ingest rule")
    @ApiResponse(
            responseCode = "200",
            description = "Activated rule",
            content = @Content(schema = @Schema(implementation = IngestDtos.GrabberRule.class)))
    public IngestDtos.GrabberRule activate(@PathVariable UUID ruleId, HttpServletRequest httpRequest) {
        return ingestService.activate(
                SecurityAuthSupport.requirePrincipal().userId(), ruleId, ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/ingest/rules/{ruleId}/pause")
    @Operation(operationId = "ingestRulesPause", summary = "Pause ingest rule")
    @ApiResponse(
            responseCode = "200",
            description = "Paused rule",
            content = @Content(schema = @Schema(implementation = IngestDtos.GrabberRule.class)))
    public IngestDtos.GrabberRule pause(@PathVariable UUID ruleId, HttpServletRequest httpRequest) {
        return ingestService.pause(
                SecurityAuthSupport.requirePrincipal().userId(), ruleId, ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/ingest/rules/{ruleId}/resume")
    @Operation(operationId = "ingestRulesResume", summary = "Resume ingest rule")
    @ApiResponse(
            responseCode = "200",
            description = "Resumed rule",
            content = @Content(schema = @Schema(implementation = IngestDtos.GrabberRule.class)))
    public IngestDtos.GrabberRule resume(@PathVariable UUID ruleId, HttpServletRequest httpRequest) {
        return ingestService.resume(
                SecurityAuthSupport.requirePrincipal().userId(), ruleId, ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/ingest/rules/{ruleId}/archive")
    @Operation(operationId = "ingestRulesArchive", summary = "Archive ingest rule")
    @ApiResponse(
            responseCode = "200",
            description = "Archived rule",
            content = @Content(schema = @Schema(implementation = IngestDtos.GrabberRule.class)))
    public IngestDtos.GrabberRule archive(@PathVariable UUID ruleId, HttpServletRequest httpRequest) {
        return ingestService.archive(
                SecurityAuthSupport.requirePrincipal().userId(), ruleId, ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/ingest/rules/{ruleId}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "ingestRulesRun", summary = "Manual grabber run (Check now)")
    @ApiResponse(
            responseCode = "202",
            description = "Run accepted",
            content = @Content(schema = @Schema(implementation = IngestDtos.GrabberRun.class)))
    public IngestDtos.GrabberRun startRun(
            @PathVariable UUID ruleId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        return ingestService.startRun(
                SecurityAuthSupport.requirePrincipal().userId(),
                ruleId,
                idempotencyKey,
                ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/api/v1/ingest/rules/{ruleId}/runs")
    @Operation(operationId = "ingestRunsList", summary = "List grabber runs for a rule")
    @ApiResponse(
            responseCode = "200",
            description = "Runs page",
            content = @Content(schema = @Schema(implementation = CursorPage.GrabberRunPage.class)))
    public CursorPage.GrabberRunPage listRuns(
            @PathVariable UUID ruleId, @Valid @ParameterObject CursorPage.Params page) {
        return ingestService.listRuns(SecurityAuthSupport.requirePrincipal().userId(), ruleId, page);
    }

    @GetMapping("/api/v1/ingest/rules/{ruleId}/items")
    @Operation(operationId = "ingestItemsList", summary = "List grabber items / moderation queue")
    @ApiResponse(
            responseCode = "200",
            description = "Items page",
            content = @Content(schema = @Schema(implementation = CursorPage.GrabberItemPage.class)))
    public CursorPage.GrabberItemPage listItems(
            @PathVariable UUID ruleId,
            @RequestParam(required = false) IngestDtos.ItemStatus status,
            @Valid @ParameterObject CursorPage.Params page) {
        return ingestService.listItems(SecurityAuthSupport.requirePrincipal().userId(), ruleId, status, page);
    }

    @GetMapping("/api/v1/ingest/items/{itemId}")
    @Operation(operationId = "ingestItemsGet", summary = "Get grabber item details")
    @ApiResponse(
            responseCode = "200",
            description = "Item",
            content = @Content(schema = @Schema(implementation = IngestDtos.GrabberItem.class)))
    public IngestDtos.GrabberItem getItem(@PathVariable UUID itemId) {
        return ingestService.getItem(SecurityAuthSupport.requirePrincipal().userId(), itemId);
    }

    @PostMapping("/api/v1/ingest/items/{itemId}/approve")
    @Operation(operationId = "ingestItemsApprove", summary = "Approve grabber item → publish via pipeline")
    @ApiResponse(
            responseCode = "200",
            description = "Approved item",
            content = @Content(schema = @Schema(implementation = IngestDtos.GrabberItem.class)))
    public IngestDtos.GrabberItem approve(
            @PathVariable UUID itemId,
            @RequestBody(required = false) IngestDtos.GrabberItemApproveRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        return ingestService.approve(
                SecurityAuthSupport.requirePrincipal().userId(),
                itemId,
                request,
                idempotencyKey,
                ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/ingest/items/{itemId}/reject")
    @Operation(operationId = "ingestItemsReject", summary = "Reject grabber item")
    @ApiResponse(
            responseCode = "200",
            description = "Rejected item",
            content = @Content(schema = @Schema(implementation = IngestDtos.GrabberItem.class)))
    public IngestDtos.GrabberItem reject(
            @PathVariable UUID itemId,
            @RequestBody(required = false) IngestDtos.GrabberItemRejectRequest request,
            HttpServletRequest httpRequest) {
        return ingestService.reject(
                SecurityAuthSupport.requirePrincipal().userId(),
                itemId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/ingest/items/{itemId}/edit-and-approve")
    @Operation(operationId = "ingestItemsEditAndApprove", summary = "Edit text and approve grabber item")
    @ApiResponse(
            responseCode = "200",
            description = "Edited and approved item",
            content = @Content(schema = @Schema(implementation = IngestDtos.GrabberItem.class)))
    public IngestDtos.GrabberItem editAndApprove(
            @PathVariable UUID itemId,
            @Valid @RequestBody IngestDtos.GrabberItemEditApproveRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        return ingestService.editAndApprove(
                SecurityAuthSupport.requirePrincipal().userId(),
                itemId,
                request,
                idempotencyKey,
                ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/ingest/items/bulk")
    @Operation(operationId = "ingestItemsBulk", summary = "Bulk approve/reject grabber items")
    @ApiResponse(
            responseCode = "200",
            description = "Bulk result",
            content = @Content(schema = @Schema(implementation = IngestDtos.GrabberBulkModerationResponse.class)))
    public IngestDtos.GrabberBulkModerationResponse bulk(
            @Valid @RequestBody IngestDtos.GrabberBulkModerationRequest request, HttpServletRequest httpRequest) {
        return ingestService.bulk(
                SecurityAuthSupport.requirePrincipal().userId(), request, ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/ingest/preview")
    @Operation(operationId = "ingestPreview", summary = "Preview source/RSS parse without saving")
    @ApiResponse(
            responseCode = "200",
            description = "Preview items",
            content = @Content(schema = @Schema(implementation = IngestDtos.IngestPreviewResponse.class)))
    public IngestDtos.IngestPreviewResponse preview(
            @RequestParam UUID workspaceId, @Valid @RequestBody IngestDtos.IngestPreviewRequest request) {
        return ingestService.preview(SecurityAuthSupport.requirePrincipal().userId(), workspaceId, request);
    }
}
