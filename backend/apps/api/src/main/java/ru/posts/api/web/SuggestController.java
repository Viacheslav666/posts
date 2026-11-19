package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
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
import ru.posts.api.automation.suggest.SuggestService;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.suggest.SuggestDtos;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@Validated
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Suggest", description = "VK suggested posts automation (BE-34)")
@OpenApiResponses
public class SuggestController {

    private final SuggestService suggestService;

    public SuggestController(SuggestService suggestService) {
        this.suggestService = suggestService;
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/suggest/settings")
    @Operation(operationId = "suggestSettingsList", summary = "List suggest settings in a project")
    @ApiResponse(
            responseCode = "200",
            description = "Settings page",
            content = @Content(schema = @Schema(implementation = CursorPage.SuggestSettingsPage.class)))
    public CursorPage.SuggestSettingsPage listSettings(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @ParameterObject CursorPage.Params page) {
        return suggestService.listSettings(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, projectId, page);
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/suggest/settings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "suggestSettingsCreate", summary = "Create suggest settings for a VK channel")
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(implementation = SuggestDtos.SuggestSettings.class)))
    public SuggestDtos.SuggestSettings createSettings(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody SuggestDtos.SuggestSettingsCreate request,
            HttpServletRequest httpRequest) {
        return suggestService.createSettings(
                SecurityAuthSupport.requirePrincipal().userId(),
                workspaceId,
                projectId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/api/v1/suggest/settings/{settingsId}")
    @Operation(operationId = "suggestSettingsGet", summary = "Get suggest settings")
    @ApiResponse(
            responseCode = "200",
            description = "Suggest settings",
            content = @Content(schema = @Schema(implementation = SuggestDtos.SuggestSettings.class)))
    public SuggestDtos.SuggestSettings getSettings(@PathVariable UUID settingsId) {
        return suggestService.getSettings(SecurityAuthSupport.requirePrincipal().userId(), settingsId);
    }

    @PatchMapping("/api/v1/suggest/settings/{settingsId}")
    @Operation(operationId = "suggestSettingsPatch", summary = "Patch suggest settings")
    @ApiResponse(
            responseCode = "200",
            description = "Updated settings",
            content = @Content(schema = @Schema(implementation = SuggestDtos.SuggestSettings.class)))
    public SuggestDtos.SuggestSettings patchSettings(
            @PathVariable UUID settingsId,
            @Valid @RequestBody SuggestDtos.SuggestSettingsPatch request,
            HttpServletRequest httpRequest) {
        return suggestService.patchSettings(
                SecurityAuthSupport.requirePrincipal().userId(),
                settingsId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/suggest/settings/{settingsId}/activate")
    @Operation(operationId = "suggestSettingsActivate", summary = "Activate suggest settings")
    @ApiResponse(
            responseCode = "200",
            description = "Activated settings",
            content = @Content(schema = @Schema(implementation = SuggestDtos.SuggestSettings.class)))
    public SuggestDtos.SuggestSettings activate(@PathVariable UUID settingsId, HttpServletRequest httpRequest) {
        return suggestService.activate(
                SecurityAuthSupport.requirePrincipal().userId(), settingsId, ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/suggest/settings/{settingsId}/pause")
    @Operation(operationId = "suggestSettingsPause", summary = "Pause suggest settings")
    @ApiResponse(
            responseCode = "200",
            description = "Paused settings",
            content = @Content(schema = @Schema(implementation = SuggestDtos.SuggestSettings.class)))
    public SuggestDtos.SuggestSettings pause(@PathVariable UUID settingsId, HttpServletRequest httpRequest) {
        return suggestService.pause(
                SecurityAuthSupport.requirePrincipal().userId(), settingsId, ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/suggest/settings/{settingsId}/resume")
    @Operation(operationId = "suggestSettingsResume", summary = "Resume suggest settings")
    @ApiResponse(
            responseCode = "200",
            description = "Resumed settings",
            content = @Content(schema = @Schema(implementation = SuggestDtos.SuggestSettings.class)))
    public SuggestDtos.SuggestSettings resume(@PathVariable UUID settingsId, HttpServletRequest httpRequest) {
        return suggestService.resume(
                SecurityAuthSupport.requirePrincipal().userId(), settingsId, ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/suggest/settings/{settingsId}/copy")
    @Operation(operationId = "suggestSettingsCopy", summary = "Copy suggest settings to channels")
    @ApiResponse(
            responseCode = "200",
            description = "Copy report",
            content = @Content(schema = @Schema(implementation = SuggestDtos.SuggestSettingsCopyResult.class)))
    public SuggestDtos.SuggestSettingsCopyResult copy(
            @PathVariable UUID settingsId,
            @Valid @RequestBody SuggestDtos.SuggestSettingsCopyRequest request,
            HttpServletRequest httpRequest) {
        return suggestService.copy(
                SecurityAuthSupport.requirePrincipal().userId(),
                settingsId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/suggest/settings/{settingsId}/sync-last-100")
    @Operation(operationId = "suggestSettingsSync", summary = "Sync last 100 suggested posts from VK")
    @ApiResponse(
            responseCode = "200",
            description = "Sync result",
            content = @Content(schema = @Schema(implementation = SuggestDtos.SuggestSyncResult.class)))
    public SuggestDtos.SuggestSyncResult sync(@PathVariable UUID settingsId, HttpServletRequest httpRequest) {
        return suggestService.syncLast100(
                SecurityAuthSupport.requirePrincipal().userId(), settingsId, ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/api/v1/suggest/settings/{settingsId}/items")
    @Operation(operationId = "suggestItemsList", summary = "List suggest items")
    @ApiResponse(
            responseCode = "200",
            description = "Items page",
            content = @Content(schema = @Schema(implementation = CursorPage.SuggestItemPage.class)))
    public CursorPage.SuggestItemPage listItems(
            @PathVariable UUID settingsId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String authorVkId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @Valid @ParameterObject CursorPage.Params page) {
        return suggestService.listItems(
                SecurityAuthSupport.requirePrincipal().userId(), settingsId, status, authorVkId, from, to, page);
    }

    @GetMapping("/api/v1/suggest/items/{itemId}")
    @Operation(operationId = "suggestItemsGet", summary = "Get suggest item")
    @ApiResponse(
            responseCode = "200",
            description = "Suggest item",
            content = @Content(schema = @Schema(implementation = SuggestDtos.SuggestItem.class)))
    public SuggestDtos.SuggestItem getItem(@PathVariable UUID itemId) {
        return suggestService.getItem(SecurityAuthSupport.requirePrincipal().userId(), itemId);
    }

    @PostMapping("/api/v1/suggest/items/{itemId}/approve")
    @Operation(operationId = "suggestItemsApprove", summary = "Approve suggest item")
    @ApiResponse(
            responseCode = "200",
            description = "Approved item",
            content = @Content(schema = @Schema(implementation = SuggestDtos.SuggestItem.class)))
    public SuggestDtos.SuggestItem approve(
            @PathVariable UUID itemId,
            @RequestBody(required = false) SuggestDtos.SuggestItemApproveRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        return suggestService.approve(
                SecurityAuthSupport.requirePrincipal().userId(),
                itemId,
                request,
                idempotencyKey,
                ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/suggest/items/{itemId}/reject")
    @Operation(operationId = "suggestItemsReject", summary = "Reject suggest item")
    @ApiResponse(
            responseCode = "200",
            description = "Rejected item",
            content = @Content(schema = @Schema(implementation = SuggestDtos.SuggestItem.class)))
    public SuggestDtos.SuggestItem reject(
            @PathVariable UUID itemId,
            @RequestBody(required = false) SuggestDtos.SuggestItemRejectRequest request,
            HttpServletRequest httpRequest) {
        return suggestService.reject(
                SecurityAuthSupport.requirePrincipal().userId(),
                itemId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/suggest/settings/{settingsId}/items/bulk")
    @Operation(operationId = "suggestItemsBulk", summary = "Bulk approve/reject suggest items")
    @ApiResponse(
            responseCode = "200",
            description = "Bulk job",
            content = @Content(schema = @Schema(implementation = SuggestDtos.SuggestBulkJob.class)))
    public SuggestDtos.SuggestBulkJob bulk(
            @PathVariable UUID settingsId,
            @Valid @RequestBody SuggestDtos.SuggestBulkRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        return suggestService.bulk(
                SecurityAuthSupport.requirePrincipal().userId(),
                settingsId,
                request,
                idempotencyKey,
                ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/api/v1/suggest/bulk-jobs/{jobId}")
    @Operation(operationId = "suggestBulkJobGet", summary = "Get bulk moderation job progress")
    @ApiResponse(
            responseCode = "200",
            description = "Bulk job progress",
            content = @Content(schema = @Schema(implementation = SuggestDtos.SuggestBulkJob.class)))
    public SuggestDtos.SuggestBulkJob getBulkJob(@PathVariable UUID jobId) {
        return suggestService.getBulkJob(SecurityAuthSupport.requirePrincipal().userId(), jobId);
    }
}
