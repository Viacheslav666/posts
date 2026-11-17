package ru.posts.api.media;

import java.util.UUID;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.media.MediaDtos;

public interface MediaService {

    CursorPage.MediaPage list(
            UUID userId,
            UUID workspaceId,
            ApiEnums.MediaKind kind,
            ApiEnums.MediaStatus status,
            String q,
            String folder,
            String tag,
            CursorPage.Params page);

    MediaDtos.MediaAsset get(UUID userId, UUID mediaId);

    MediaDtos.CreateUploadResponse createUpload(
            UUID userId, MediaDtos.CreateUploadRequest request, String idempotencyKey, ClientRequestContext ctx);

    MediaDtos.MediaAsset complete(
            UUID userId, UUID mediaId, MediaDtos.CompleteUploadRequest request, ClientRequestContext ctx);

    AuthDtos.MessageResponse delete(UUID userId, UUID mediaId, ClientRequestContext ctx);

    MediaDtos.ImportUrlResponse importUrl(
            UUID userId, MediaDtos.ImportUrlRequest request, String idempotencyKey, ClientRequestContext ctx);
}
