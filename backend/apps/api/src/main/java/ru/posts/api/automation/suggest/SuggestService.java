package ru.posts.api.automation.suggest;

import java.time.Instant;
import java.util.UUID;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.suggest.SuggestDtos;

public interface SuggestService {

    CursorPage.SuggestSettingsPage listSettings(UUID userId, UUID workspaceId, UUID projectId, CursorPage.Params page);

    SuggestDtos.SuggestSettings createSettings(
            UUID userId,
            UUID workspaceId,
            UUID projectId,
            SuggestDtos.SuggestSettingsCreate request,
            ClientRequestContext ctx);

    SuggestDtos.SuggestSettings getSettings(UUID userId, UUID settingsId);

    SuggestDtos.SuggestSettings patchSettings(
            UUID userId, UUID settingsId, SuggestDtos.SuggestSettingsPatch request, ClientRequestContext ctx);

    SuggestDtos.SuggestSettings activate(UUID userId, UUID settingsId, ClientRequestContext ctx);

    SuggestDtos.SuggestSettings pause(UUID userId, UUID settingsId, ClientRequestContext ctx);

    SuggestDtos.SuggestSettings resume(UUID userId, UUID settingsId, ClientRequestContext ctx);

    SuggestDtos.SuggestSettingsCopyResult copy(
            UUID userId, UUID settingsId, SuggestDtos.SuggestSettingsCopyRequest request, ClientRequestContext ctx);

    SuggestDtos.SuggestSyncResult syncLast100(UUID userId, UUID settingsId, ClientRequestContext ctx);

    CursorPage.SuggestItemPage listItems(
            UUID userId,
            UUID settingsId,
            String status,
            String authorVkId,
            Instant from,
            Instant to,
            CursorPage.Params page);

    SuggestDtos.SuggestItem getItem(UUID userId, UUID itemId);

    SuggestDtos.SuggestItem approve(
            UUID userId,
            UUID itemId,
            SuggestDtos.SuggestItemApproveRequest request,
            String idempotencyKey,
            ClientRequestContext ctx);

    SuggestDtos.SuggestItem reject(
            UUID userId, UUID itemId, SuggestDtos.SuggestItemRejectRequest request, ClientRequestContext ctx);

    SuggestDtos.SuggestBulkJob bulk(
            UUID userId,
            UUID settingsId,
            SuggestDtos.SuggestBulkRequest request,
            String idempotencyKey,
            ClientRequestContext ctx);

    SuggestDtos.SuggestBulkJob getBulkJob(UUID userId, UUID jobId);
}
