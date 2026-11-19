package ru.posts.api.crosspost;

import java.util.UUID;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.web.dto.crosspost.CrosspostDtos;

public interface CrosspostService {

    CrosspostDtos.RuleList listRules(UUID userId, UUID workspaceId, UUID projectId);

    CrosspostDtos.Rule createRule(
            UUID userId,
            UUID workspaceId,
            UUID projectId,
            CrosspostDtos.RuleCreate request,
            String idempotencyKey,
            ClientRequestContext ctx);

    CrosspostDtos.Rule getRule(UUID userId, UUID ruleId);

    CrosspostDtos.Rule patchRule(
            UUID userId, UUID ruleId, CrosspostDtos.RulePatch request, ClientRequestContext ctx);

    CrosspostDtos.Rule activate(UUID userId, UUID ruleId, ClientRequestContext ctx);

    CrosspostDtos.Rule pause(UUID userId, UUID ruleId, ClientRequestContext ctx);

    CrosspostDtos.PreviewResponse preview(UUID userId, UUID ruleId, CrosspostDtos.PreviewRequest request);

    CrosspostDtos.ItemPage listItems(UUID userId, UUID ruleId, String status, String cursor, int limit);

    CrosspostDtos.Item retry(UUID userId, UUID itemId, ClientRequestContext ctx);
}
