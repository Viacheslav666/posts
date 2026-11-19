package ru.posts.api.antispam;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.posts.api.web.dto.antispam.AntispamDtos;
import ru.posts.api.web.dto.common.CursorPage;

public interface AntispamService {

    AntispamDtos.AntispamRulePage listRules(UUID userId, UUID workspaceId, UUID projectId, CursorPage.Params page);

    AntispamDtos.AntispamRule createRule(
            UUID userId, UUID workspaceId, UUID projectId, AntispamDtos.AntispamRuleCreate request);

    AntispamDtos.AntispamRule getRule(UUID userId, UUID ruleId);

    AntispamDtos.AntispamRule patchRule(UUID userId, UUID ruleId, AntispamDtos.AntispamRulePatch request);

    AntispamDtos.AntispamRule activate(UUID userId, UUID ruleId);

    AntispamDtos.AntispamRule pause(UUID userId, UUID ruleId);

    AntispamDtos.AntispamRule resume(UUID userId, UUID ruleId);

    AntispamDtos.AntispamRule archive(UUID userId, UUID ruleId);

    AntispamDtos.AntispamWordListPage listWordLists(UUID userId, UUID workspaceId, CursorPage.Params page);

    AntispamDtos.AntispamWordList createWordList(
            UUID userId, UUID workspaceId, AntispamDtos.AntispamWordListCreate request);

    AntispamDtos.AntispamWordList patchWordList(
            UUID userId, UUID listId, AntispamDtos.AntispamWordListPatch request);

    void deleteWordList(UUID userId, UUID listId);

    AntispamDtos.AntispamLogPage listLog(
            UUID userId,
            UUID workspaceId,
            UUID channelId,
            String section,
            UUID ruleId,
            String action,
            Instant from,
            Instant to,
            CursorPage.Params page);

    AntispamDtos.AntispamLogEntry restore(
            UUID userId, UUID logId, AntispamDtos.AntispamRestoreRequest request);

    AntispamDtos.AntispamScanResponse scan(UUID userId, UUID ruleId);

    AntispamDtos.AntispamRuleStats stats(UUID userId, UUID ruleId);
}
