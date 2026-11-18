package ru.posts.api.config;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.channel.ChannelService;
import ru.posts.api.channel.ChannelSetService;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.channel.ChannelDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;

@Configuration
@Profile("openapi")
public class OpenApiChannelStubConfig {

    @Bean
    ChannelService channelService() {
        return new ChannelService() {
            @Override
            public CursorPage.ChannelPage list(
                    UUID userId,
                    UUID workspaceId,
                    UUID projectId,
                    ApiEnums.Network network,
                    ApiEnums.ChannelStatus status,
                    CursorPage.Params page) {
                return new CursorPage.ChannelPage(List.of(), null, false);
            }

            @Override
            public ChannelDtos.Channel get(UUID userId, UUID channelId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public ChannelDtos.Channel update(
                    UUID userId, UUID channelId, ChannelDtos.UpdateChannelRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse delete(UUID userId, UUID channelId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public ChannelDtos.ChannelConnectStartResponse connectStart(
                    UUID userId, ApiEnums.Network network, UUID workspaceId, UUID projectId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public CursorPage.ChannelPage connectCallback(
                    UUID userId,
                    ApiEnums.Network network,
                    ChannelDtos.ChannelConnectCallbackRequest request,
                    ClientRequestContext ctx) {
                return new CursorPage.ChannelPage(List.of(), null, false);
            }

            @Override
            public CursorPage.ChannelPage connectConfirm(
                    UUID userId,
                    ApiEnums.Network network,
                    ChannelDtos.ChannelConnectConfirmRequest request,
                    ClientRequestContext ctx) {
                return new CursorPage.ChannelPage(List.of(), null, false);
            }

            @Override
            public ChannelDtos.ChannelCheckResponse check(UUID userId, UUID channelId, ClientRequestContext ctx) {
                return new ChannelDtos.ChannelCheckResponse(
                        channelId, ApiEnums.ChannelStatus.active, null, Instant.now());
            }
        };
    }

    @Bean
    ChannelSetService channelSetService() {
        return new ChannelSetService() {
            @Override
            public CursorPage.ChannelSetPage list(UUID userId, UUID workspaceId, CursorPage.Params page) {
                return new CursorPage.ChannelSetPage(List.of(), null, false);
            }

            @Override
            public ChannelDtos.ChannelSet create(
                    UUID userId,
                    UUID workspaceId,
                    ChannelDtos.CreateChannelSetRequest request,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public ChannelDtos.ChannelSet get(UUID userId, UUID setId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public ChannelDtos.ChannelSet update(
                    UUID userId, UUID setId, ChannelDtos.UpdateChannelSetRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse delete(UUID userId, UUID setId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }
        };
    }
}
