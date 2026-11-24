package ru.posts.api.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.channel.ChannelDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.workspace.AuditRecorder;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.persistence.entity.ChannelConnectStateEntity;
import ru.posts.persistence.repository.ChannelConnectStateRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.OutboxRepository;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.TelegramBotRepository;

class ChannelConnectCallbackOrderTest {

    private WorkspaceAccessService access;
    private ChannelConnectStateRepository connectStateRepository;
    private ChannelServiceImpl service;

    @BeforeEach
    void setUp() {
        access = mock(WorkspaceAccessService.class);
        connectStateRepository = mock(ChannelConnectStateRepository.class);
        service = new ChannelServiceImpl(
                access,
                mock(ChannelRepository.class),
                mock(SocialAccountRepository.class),
                connectStateRepository,
                mock(TelegramBotRepository.class),
                mock(PlanLimitService.class),
                mock(WorkspaceTokenVault.class),
                mock(SocialConnectClient.class),
                new ChannelProperties(),
                mock(VkSocialAccountTokenRefreshService.class),
                mock(AuditRecorder.class),
                mock(OutboxRepository.class));
    }

    @Test
    void workspaceIdPresentRequiresManageBeforeStateLookup() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(access.requirePermission(workspaceId, userId, "channels.manage"))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, ProblemCodes.FORBIDDEN, "Insufficient permissions"));

        assertThatThrownBy(() -> service.connectCallback(
                        userId,
                        ApiEnums.Network.vk,
                        callback(workspaceId, "rbac-probe-state"),
                        new ClientRequestContext("127.0.0.1", "test")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(api.getCode()).isEqualTo(ProblemCodes.FORBIDDEN);
                });

        verifyNoInteractions(connectStateRepository);
    }

    @Test
    void omittedWorkspaceIdRejectsForeignStateBeforePermission() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        ChannelConnectStateEntity state = state(ownerId, workspaceId);
        when(connectStateRepository.lockByState("connect-state")).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> service.connectCallback(
                        otherId,
                        ApiEnums.Network.vk,
                        callback(null, "connect-state"),
                        new ClientRequestContext("127.0.0.1", "test")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.getCode()).isEqualTo(ProblemCodes.INVALID_TOKEN);
                });

        verify(access, never()).requirePermission(workspaceId, otherId, "channels.manage");
    }

    @Test
    void omittedWorkspaceIdChecksManageAfterStateOwnership() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(connectStateRepository.lockByState("connect-state")).thenReturn(Optional.of(state(userId, workspaceId)));
        when(access.requirePermission(workspaceId, userId, "channels.manage"))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, ProblemCodes.FORBIDDEN, "Insufficient permissions"));

        assertThatThrownBy(() -> service.connectCallback(
                        userId,
                        ApiEnums.Network.vk,
                        callback(null, "connect-state"),
                        new ClientRequestContext("127.0.0.1", "test")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ProblemCodes.FORBIDDEN));

        verify(access).requirePermission(workspaceId, userId, "channels.manage");
    }

    private static ChannelDtos.ChannelConnectCallbackRequest callback(UUID workspaceId, String state) {
        return new ChannelDtos.ChannelConnectCallbackRequest(
                "vk-admin", state, workspaceId, null, null, null, null, null, null, null);
    }

    private static ChannelConnectStateEntity state(UUID userId, UUID workspaceId) {
        ChannelConnectStateEntity state = new ChannelConnectStateEntity();
        state.setState("connect-state");
        state.setUserId(userId);
        state.setWorkspaceId(workspaceId);
        state.setNetwork("vk");
        state.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        return state;
    }
}
