package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.auth.ClientRequestContexts;
import ru.posts.api.crosspost.CrosspostService;
import ru.posts.api.web.dto.crosspost.CrosspostDtos;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@Profile("!openapi")
@Validated
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Crosspost", description = "Crosspost rules and journal (BE-36)")
@OpenApiResponses
public class CrosspostController {

    private final CrosspostService crosspostService;

    public CrosspostController(CrosspostService crosspostService) {
        this.crosspostService = crosspostService;
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/crosspost/rules")
    @Operation(operationId = "crosspostRulesList", summary = "List crosspost rules")
    public CrosspostDtos.RuleList list(@PathVariable UUID workspaceId, @PathVariable UUID projectId) {
        UUID userId = SecurityAuthSupport.requirePrincipal().userId();
        return crosspostService.listRules(userId, workspaceId, projectId);
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/crosspost/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "crosspostRulesCreate", summary = "Create crosspost rule")
    public CrosspostDtos.Rule create(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody CrosspostDtos.RuleCreate request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        UUID userId = SecurityAuthSupport.requirePrincipal().userId();
        return crosspostService.createRule(
                userId, workspaceId, projectId, request, idempotencyKey, ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/api/v1/crosspost/rules/{ruleId}")
    @Operation(operationId = "crosspostRulesGet", summary = "Get crosspost rule")
    public CrosspostDtos.Rule get(@PathVariable UUID ruleId) {
        UUID userId = SecurityAuthSupport.requirePrincipal().userId();
        return crosspostService.getRule(userId, ruleId);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/api/v1/crosspost/rules/{ruleId}")
    @Operation(operationId = "crosspostRulesPatch", summary = "Patch crosspost rule")
    public CrosspostDtos.Rule patch(
            @PathVariable UUID ruleId,
            @Valid @RequestBody CrosspostDtos.RulePatch request,
            HttpServletRequest httpRequest) {
        UUID userId = SecurityAuthSupport.requirePrincipal().userId();
        return crosspostService.patchRule(userId, ruleId, request, ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/crosspost/rules/{ruleId}/activate")
    @Operation(operationId = "crosspostRulesActivate", summary = "Activate crosspost rule")
    public CrosspostDtos.Rule activate(@PathVariable UUID ruleId, HttpServletRequest httpRequest) {
        UUID userId = SecurityAuthSupport.requirePrincipal().userId();
        return crosspostService.activate(userId, ruleId, ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/crosspost/rules/{ruleId}/pause")
    @Operation(operationId = "crosspostRulesPause", summary = "Pause crosspost rule")
    public CrosspostDtos.Rule pause(@PathVariable UUID ruleId, HttpServletRequest httpRequest) {
        UUID userId = SecurityAuthSupport.requirePrincipal().userId();
        return crosspostService.pause(userId, ruleId, ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/api/v1/crosspost/rules/{ruleId}/preview")
    @Operation(operationId = "crosspostRulesPreview", summary = "Preview crosspost transforms without publishing")
    public CrosspostDtos.PreviewResponse preview(
            @PathVariable UUID ruleId, @RequestBody CrosspostDtos.PreviewRequest request) {
        UUID userId = SecurityAuthSupport.requirePrincipal().userId();
        return crosspostService.preview(userId, ruleId, request);
    }

    @GetMapping("/api/v1/crosspost/rules/{ruleId}/items")
    @Operation(operationId = "crosspostItemsList", summary = "List crosspost journal items")
    public CrosspostDtos.ItemPage items(
            @PathVariable UUID ruleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        UUID userId = SecurityAuthSupport.requirePrincipal().userId();
        return crosspostService.listItems(userId, ruleId, status, cursor, limit);
    }

    @PostMapping("/api/v1/crosspost/items/{itemId}/retry")
    @Operation(operationId = "crosspostItemsRetry", summary = "Retry a failed crosspost item")
    public CrosspostDtos.Item retry(@PathVariable UUID itemId, HttpServletRequest httpRequest) {
        UUID userId = SecurityAuthSupport.requirePrincipal().userId();
        return crosspostService.retry(userId, itemId, ClientRequestContexts.from(httpRequest));
    }
}
