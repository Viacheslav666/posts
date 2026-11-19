package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
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
import ru.posts.api.antispam.AntispamService;
import ru.posts.api.web.dto.antispam.AntispamDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@Validated
@Profile("!openapi")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Antispam", description = "Antispam rules, word lists, history and restore (BE-35)")
@OpenApiResponses
public class AntispamController {

    private final AntispamService antispamService;

    public AntispamController(AntispamService antispamService) {
        this.antispamService = antispamService;
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/antispam/rules")
    @Operation(operationId = "antispamRulesList", summary = "List antispam rules in a project")
    public AntispamDtos.AntispamRulePage listRules(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @ParameterObject CursorPage.Params page) {
        return antispamService.listRules(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, projectId, page);
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/antispam/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "antispamRulesCreate", summary = "Create an antispam rule")
    public AntispamDtos.AntispamRule createRule(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody AntispamDtos.AntispamRuleCreate request) {
        return antispamService.createRule(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, projectId, request);
    }

    @GetMapping("/api/v1/antispam/rules/{ruleId}")
    @Operation(operationId = "antispamRulesGet", summary = "Get antispam rule")
    public AntispamDtos.AntispamRule getRule(@PathVariable UUID ruleId) {
        return antispamService.getRule(SecurityAuthSupport.requirePrincipal().userId(), ruleId);
    }

    @PatchMapping("/api/v1/antispam/rules/{ruleId}")
    @Operation(operationId = "antispamRulesPatch", summary = "Update antispam rule")
    public AntispamDtos.AntispamRule patchRule(
            @PathVariable UUID ruleId, @Valid @RequestBody AntispamDtos.AntispamRulePatch request) {
        return antispamService.patchRule(
                SecurityAuthSupport.requirePrincipal().userId(), ruleId, request);
    }

    @PostMapping("/api/v1/antispam/rules/{ruleId}/activate")
    @Operation(operationId = "antispamRulesActivate", summary = "Activate antispam rule and ensure event subscription")
    public AntispamDtos.AntispamRule activate(@PathVariable UUID ruleId) {
        return antispamService.activate(SecurityAuthSupport.requirePrincipal().userId(), ruleId);
    }

    @PostMapping("/api/v1/antispam/rules/{ruleId}/pause")
    @Operation(operationId = "antispamRulesPause", summary = "Pause antispam rule")
    public AntispamDtos.AntispamRule pause(@PathVariable UUID ruleId) {
        return antispamService.pause(SecurityAuthSupport.requirePrincipal().userId(), ruleId);
    }

    @PostMapping("/api/v1/antispam/rules/{ruleId}/resume")
    @Operation(operationId = "antispamRulesResume", summary = "Resume paused antispam rule")
    public AntispamDtos.AntispamRule resume(@PathVariable UUID ruleId) {
        return antispamService.resume(SecurityAuthSupport.requirePrincipal().userId(), ruleId);
    }

    @PostMapping("/api/v1/antispam/rules/{ruleId}/archive")
    @Operation(operationId = "antispamRulesArchive", summary = "Archive antispam rule")
    public AntispamDtos.AntispamRule archive(@PathVariable UUID ruleId) {
        return antispamService.archive(SecurityAuthSupport.requirePrincipal().userId(), ruleId);
    }

    @PostMapping("/api/v1/antispam/rules/{ruleId}/scan")
    @Operation(operationId = "antispamRulesScan", summary = "Manual scan of last 100 VK wall comments")
    public AntispamDtos.AntispamScanResponse scan(@PathVariable UUID ruleId) {
        return antispamService.scan(SecurityAuthSupport.requirePrincipal().userId(), ruleId);
    }

    @GetMapping("/api/v1/antispam/rules/{ruleId}/stats")
    @Operation(operationId = "antispamRulesStats", summary = "Per-rule hit statistics")
    public AntispamDtos.AntispamRuleStats stats(@PathVariable UUID ruleId) {
        return antispamService.stats(SecurityAuthSupport.requirePrincipal().userId(), ruleId);
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/antispam/word-lists")
    @Operation(operationId = "antispamWordListsList", summary = "List workspace antispam word lists")
    public AntispamDtos.AntispamWordListPage listWordLists(
            @PathVariable UUID workspaceId, @Valid @ParameterObject CursorPage.Params page) {
        return antispamService.listWordLists(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, page);
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/antispam/word-lists")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "antispamWordListsCreate", summary = "Create workspace word list")
    public AntispamDtos.AntispamWordList createWordList(
            @PathVariable UUID workspaceId, @Valid @RequestBody AntispamDtos.AntispamWordListCreate request) {
        return antispamService.createWordList(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, request);
    }

    @PatchMapping("/api/v1/antispam/word-lists/{listId}")
    @Operation(operationId = "antispamWordListsPatch", summary = "Update word list")
    public AntispamDtos.AntispamWordList patchWordList(
            @PathVariable UUID listId, @Valid @RequestBody AntispamDtos.AntispamWordListPatch request) {
        return antispamService.patchWordList(
                SecurityAuthSupport.requirePrincipal().userId(), listId, request);
    }

    @DeleteMapping("/api/v1/antispam/word-lists/{listId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "antispamWordListsDelete", summary = "Delete word list")
    public void deleteWordList(@PathVariable UUID listId) {
        antispamService.deleteWordList(SecurityAuthSupport.requirePrincipal().userId(), listId);
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/antispam/log")
    @Operation(operationId = "antispamLogList", summary = "Antispam action history")
    public AntispamDtos.AntispamLogPage listLog(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) UUID ruleId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @Valid @ParameterObject CursorPage.Params page) {
        return antispamService.listLog(
                SecurityAuthSupport.requirePrincipal().userId(),
                workspaceId,
                channelId,
                section,
                ruleId,
                action,
                from,
                to,
                page);
    }

    @PostMapping("/api/v1/antispam/log/{logId}/restore")
    @Operation(operationId = "antispamLogRestore", summary = "Restore deleted VK comment from antispam log")
    public AntispamDtos.AntispamLogEntry restore(
            @PathVariable UUID logId, @RequestBody(required = false) AntispamDtos.AntispamRestoreRequest request) {
        return antispamService.restore(
                SecurityAuthSupport.requirePrincipal().userId(),
                logId,
                request == null ? new AntispamDtos.AntispamRestoreRequest(false) : request);
    }
}
