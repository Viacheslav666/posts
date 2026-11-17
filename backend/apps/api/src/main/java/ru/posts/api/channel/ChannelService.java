package ru.posts.api.channel;

import java.util.UUID;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.channel.ChannelDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;

public interface ChannelService {

    CursorPage.ChannelPage list(
            UUID userId,
            UUID workspaceId,
            UUID projectId,
            ApiEnums.Network network,
            ApiEnums.ChannelStatus status,
            CursorPage.Params page);

    ChannelDtos.Channel get(UUID userId, UUID channelId);

    ChannelDtos.Channel update(
            UUID userId, UUID channelId, ChannelDtos.UpdateChannelRequest request, ClientRequestContext ctx);

    AuthDtos.MessageResponse delete(UUID userId, UUID channelId, ClientRequestContext ctx);

    ChannelDtos.ChannelConnectStartResponse connectStart(
            UUID userId, ApiEnums.Network network, UUID workspaceId, UUID projectId, ClientRequestContext ctx);

    CursorPage.ChannelPage connectCallback(
            UUID userId,
            ApiEnums.Network network,
            ChannelDtos.ChannelConnectCallbackRequest request,
            ClientRequestContext ctx);

    CursorPage.ChannelPage connectConfirm(
            UUID userId,
            ApiEnums.Network network,
            ChannelDtos.ChannelConnectConfirmRequest request,
            ClientRequestContext ctx);

    ChannelDtos.ChannelCheckResponse check(UUID userId, UUID channelId, ClientRequestContext ctx);
}
