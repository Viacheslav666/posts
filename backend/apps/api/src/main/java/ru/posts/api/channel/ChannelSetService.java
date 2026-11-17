package ru.posts.api.channel;

import java.util.UUID;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.channel.ChannelDtos;
import ru.posts.api.web.dto.common.CursorPage;

public interface ChannelSetService {

    CursorPage.ChannelSetPage list(UUID userId, UUID workspaceId, CursorPage.Params page);

    ChannelDtos.ChannelSet create(
            UUID userId, UUID workspaceId, ChannelDtos.CreateChannelSetRequest request, ClientRequestContext ctx);

    ChannelDtos.ChannelSet get(UUID userId, UUID setId);

    ChannelDtos.ChannelSet update(
            UUID userId, UUID setId, ChannelDtos.UpdateChannelSetRequest request, ClientRequestContext ctx);

    AuthDtos.MessageResponse delete(UUID userId, UUID setId, ClientRequestContext ctx);
}
