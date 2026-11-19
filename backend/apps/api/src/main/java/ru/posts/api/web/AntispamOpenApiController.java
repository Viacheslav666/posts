package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.web.dto.antispam.AntispamDtos;
import ru.posts.api.web.dto.common.CursorPage;

@RestController
@Profile("openapi")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Antispam", description = "Antispam rules, word lists, history and restore (BE-35)")
@OpenApiResponses
public class AntispamOpenApiController {

    @GetMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/antispam/rules")
    @Operation(operationId = "antispamRulesList", summary = "List antispam rules in a project")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = AntispamDtos.AntispamRulePage.class)))
    public AntispamDtos.AntispamRulePage listRules(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @ParameterObject CursorPage.Params page) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/antispam/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "antispamRulesCreate", summary = "Create an antispam rule")
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(implementation = AntispamDtos.AntispamRule.class)))
    public AntispamDtos.AntispamRule createRule(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody AntispamDtos.AntispamRuleCreate request) {
        throw new NotImplementedException("openapi");
    }

    @GetMapping("/api/v1/antispam/rules/{ruleId}")
    @Operation(operationId = "antispamRulesGet", summary = "Get antispam rule")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = AntispamDtos.AntispamRule.class)))
    public AntispamDtos.AntispamRule getRule(@PathVariable UUID ruleId) {
        throw new NotImplementedException("openapi");
    }

    @PatchMapping("/api/v1/antispam/rules/{ruleId}")
    @Operation(operationId = "antispamRulesPatch", summary = "Update antispam rule")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = AntispamDtos.AntispamRule.class)))
    public AntispamDtos.AntispamRule patchRule(
            @PathVariable UUID ruleId, @Valid @RequestBody AntispamDtos.AntispamRulePatch request) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/api/v1/antispam/rules/{ruleId}/activate")
    @Operation(operationId = "antispamRulesActivate", summary = "Activate antispam rule and ensure event subscription")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = AntispamDtos.AntispamRule.class)))
    public AntispamDtos.AntispamRule activate(@PathVariable UUID ruleId) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/api/v1/antispam/rules/{ruleId}/pause")
    @Operation(operationId = "antispamRulesPause", summary = "Pause antispam rule")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = AntispamDtos.AntispamRule.class)))
    public AntispamDtos.AntispamRule pause(@PathVariable UUID ruleId) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/api/v1/antispam/rules/{ruleId}/resume")
    @Operation(operationId = "antispamRulesResume", summary = "Resume paused antispam rule")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = AntispamDtos.AntispamRule.class)))
    public AntispamDtos.AntispamRule resume(@PathVariable UUID ruleId) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/api/v1/antispam/rules/{ruleId}/archive")
    @Operation(operationId = "antispamRulesArchive", summary = "Archive antispam rule")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = AntispamDtos.AntispamRule.class)))
    public AntispamDtos.AntispamRule archive(@PathVariable UUID ruleId) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/api/v1/antispam/rules/{ruleId}/scan")
    @Operation(operationId = "antispamRulesScan", summary = "Manual scan of last 100 VK wall comments")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = AntispamDtos.AntispamScanResponse.class)))
    public AntispamDtos.AntispamScanResponse scan(@PathVariable UUID ruleId) {
        throw new NotImplementedException("openapi");
    }

    @GetMapping("/api/v1/antispam/rules/{ruleId}/stats")
    @Operation(operationId = "antispamRulesStats", summary = "Per-rule hit statistics")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = AntispamDtos.AntispamRuleStats.class)))
    public AntispamDtos.AntispamRuleStats stats(@PathVariable UUID ruleId) {
        throw new NotImplementedException("openapi");
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/antispam/word-lists")
    @Operation(operationId = "antispamWordListsList", summary = "List workspace antispam word lists")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = AntispamDtos.AntispamWordListPage.class)))
    public AntispamDtos.AntispamWordListPage listWordLists(
            @PathVariable UUID workspaceId, @Valid @ParameterObject CursorPage.Params page) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/antispam/word-lists")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "antispamWordListsCreate", summary = "Create workspace word list")
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(implementation = AntispamDtos.AntispamWordList.class)))
    public AntispamDtos.AntispamWordList createWordList(
            @PathVariable UUID workspaceId, @Valid @RequestBody AntispamDtos.AntispamWordListCreate request) {
        throw new NotImplementedException("openapi");
    }

    @PatchMapping("/api/v1/antispam/word-lists/{listId}")
    @Operation(operationId = "antispamWordListsPatch", summary = "Update word list")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = AntispamDtos.AntispamWordList.class)))
    public AntispamDtos.AntispamWordList patchWordList(
            @PathVariable UUID listId, @Valid @RequestBody AntispamDtos.AntispamWordListPatch request) {
        throw new NotImplementedException("openapi");
    }

    @DeleteMapping("/api/v1/antispam/word-lists/{listId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "antispamWordListsDelete", summary = "Delete word list")
    @ApiResponse(responseCode = "204", description = "No Content")
    public void deleteWordList(@PathVariable UUID listId) {
        throw new NotImplementedException("openapi");
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/antispam/log")
    @Operation(operationId = "antispamLogList", summary = "Antispam action history")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = AntispamDtos.AntispamLogPage.class)))
    public AntispamDtos.AntispamLogPage listLog(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) UUID ruleId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @Valid @ParameterObject CursorPage.Params page) {
        throw new NotImplementedException("openapi");
    }

    @PostMapping("/api/v1/antispam/log/{logId}/restore")
    @Operation(operationId = "antispamLogRestore", summary = "Restore deleted VK comment from antispam log")
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = AntispamDtos.AntispamLogEntry.class)))
    public AntispamDtos.AntispamLogEntry restore(
            @PathVariable UUID logId, @RequestBody(required = false) AntispamDtos.AntispamRestoreRequest request) {
        throw new NotImplementedException("openapi");
    }
}
