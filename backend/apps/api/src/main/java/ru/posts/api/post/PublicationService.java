package ru.posts.api.post;

import java.util.List;
import java.util.UUID;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.post.PostDtos;

public interface PublicationService {

    CursorPage.PublicationPage list(
            UUID userId,
            UUID workspaceId,
            UUID postId,
            UUID channelId,
            ApiEnums.PublicationState state,
            ApiEnums.Network network,
            CursorPage.Params page);

    PostDtos.Publication get(UUID userId, UUID publicationId);

    PostDtos.Publication retry(UUID userId, UUID publicationId, ClientRequestContext ctx);

    List<PostDtos.PublicationAttempt> listAttempts(UUID userId, UUID publicationId);
}
