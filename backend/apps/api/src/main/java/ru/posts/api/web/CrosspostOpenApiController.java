package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.web.dto.crosspost.CrosspostDtos;

@RestController
@Profile("openapi")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Crosspost", description = "Crosspost rules and journal (BE-36)")
@OpenApiResponses
public class CrosspostOpenApiController {

    @GetMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/crosspost/rules")
    @Operation(operationId = "crosspostRulesList", summary = "List crosspost rules")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = CrosspostDtos.RuleList.class)))
    public CrosspostDtos.RuleList list(@PathVariable UUID workspaceId, @PathVariable UUID projectId) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/crosspost/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "crosspostRulesCreate", summary = "Create crosspost rule")
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(implementation = CrosspostDtos.Rule.class)))
    public CrosspostDtos.Rule create(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody CrosspostDtos.RuleCreate request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        throw new NotImplementedException("openapi");
    }

    @GetMapping("/api/v1/crosspost/rules/{ruleId}")
    @Operation(operationId = "crosspostRulesGet", summary = "Get crosspost rule")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = CrosspostDtos.Rule.class)))
    public CrosspostDtos.Rule get(@PathVariable UUID ruleId) {
        throw new NotImplementedException("openapi");
    }

    @org.springframework.web.bind.annotation.PatchMapping("/api/v1/crosspost/rules/{ruleId}")
    @Operation(operationId = "crosspostRulesPatch", summary = "Patch crosspost rule")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = CrosspostDtos.Rule.class)))
    public CrosspostDtos.Rule patch(@PathVariable UUID ruleId, @Valid @RequestBody CrosspostDtos.RulePatch request) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/api/v1/crosspost/rules/{ruleId}/activate")
    @Operation(operationId = "crosspostRulesActivate", summary = "Activate crosspost rule")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = CrosspostDtos.Rule.class)))
    public CrosspostDtos.Rule activate(@PathVariable UUID ruleId) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/api/v1/crosspost/rules/{ruleId}/pause")
    @Operation(operationId = "crosspostRulesPause", summary = "Pause crosspost rule")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = CrosspostDtos.Rule.class)))
    public CrosspostDtos.Rule pause(@PathVariable UUID ruleId) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/api/v1/crosspost/rules/{ruleId}/preview")
    @Operation(operationId = "crosspostRulesPreview", summary = "Preview crosspost transforms without publishing")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = CrosspostDtos.PreviewResponse.class)))
    public CrosspostDtos.PreviewResponse preview(
            @PathVariable UUID ruleId, @RequestBody CrosspostDtos.PreviewRequest request) {
        throw new NotImplementedException("openapi");
    }

    @GetMapping("/api/v1/crosspost/rules/{ruleId}/items")
    @Operation(operationId = "crosspostItemsList", summary = "List crosspost journal items")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = CrosspostDtos.ItemPage.class)))
    public CrosspostDtos.ItemPage items(
            @PathVariable UUID ruleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/api/v1/crosspost/items/{itemId}/retry")
    @Operation(operationId = "crosspostItemsRetry", summary = "Retry a failed crosspost item")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = CrosspostDtos.Item.class)))
    public CrosspostDtos.Item retry(@PathVariable UUID itemId) {
        throw new NotImplementedException("openapi");
    }
}
