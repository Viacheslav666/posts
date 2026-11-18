package ru.posts.api.ingest;

import java.util.UUID;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.ingest.IngestDtos;

public interface IngestService {

    CursorPage.GrabberRulePage listRules(
            UUID userId, UUID workspaceId, UUID projectId, IngestDtos.RuleType type, CursorPage.Params page);

    IngestDtos.GrabberRule createRule(
            UUID userId,
            UUID workspaceId,
            UUID projectId,
            IngestDtos.GrabberRuleCreate request,
            String idempotencyKey,
            ClientRequestContext ctx);

    IngestDtos.GrabberRule getRule(UUID userId, UUID ruleId);

    IngestDtos.GrabberRule patchRule(
            UUID userId, UUID ruleId, IngestDtos.GrabberRulePatch request, ClientRequestContext ctx);

    IngestDtos.GrabberRule activate(UUID userId, UUID ruleId, ClientRequestContext ctx);

    IngestDtos.GrabberRule pause(UUID userId, UUID ruleId, ClientRequestContext ctx);

    IngestDtos.GrabberRule resume(UUID userId, UUID ruleId, ClientRequestContext ctx);

    IngestDtos.GrabberRule archive(UUID userId, UUID ruleId, ClientRequestContext ctx);

    IngestDtos.GrabberRun startRun(UUID userId, UUID ruleId, String idempotencyKey, ClientRequestContext ctx);

    CursorPage.GrabberRunPage listRuns(UUID userId, UUID ruleId, CursorPage.Params page);

    CursorPage.GrabberItemPage listItems(
            UUID userId, UUID ruleId, IngestDtos.ItemStatus status, CursorPage.Params page);

    IngestDtos.GrabberItem getItem(UUID userId, UUID itemId);

    IngestDtos.GrabberItem approve(
            UUID userId,
            UUID itemId,
            IngestDtos.GrabberItemApproveRequest request,
            String idempotencyKey,
            ClientRequestContext ctx);

    IngestDtos.GrabberItem reject(
            UUID userId, UUID itemId, IngestDtos.GrabberItemRejectRequest request, ClientRequestContext ctx);

    IngestDtos.GrabberItem editAndApprove(
            UUID userId,
            UUID itemId,
            IngestDtos.GrabberItemEditApproveRequest request,
            String idempotencyKey,
            ClientRequestContext ctx);

    IngestDtos.GrabberBulkModerationResponse bulk(
            UUID userId, IngestDtos.GrabberBulkModerationRequest request, ClientRequestContext ctx);

    IngestDtos.IngestPreviewResponse preview(UUID userId, UUID workspaceId, IngestDtos.IngestPreviewRequest request);
}
