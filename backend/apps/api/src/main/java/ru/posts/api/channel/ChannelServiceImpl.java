package ru.posts.api.channel;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.channel.ChannelDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.workspace.AuditRecorder;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.api.workspace.WorkspaceMembership;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.ChannelConnectStateEntity;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.OutboxEntity;
import ru.posts.persistence.entity.SocialAccountEntity;
import ru.posts.persistence.entity.TelegramBotEntity;
import ru.posts.persistence.repository.ChannelConnectStateRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.OutboxRepository;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.TelegramBotRepository;
import ru.posts.social.vk.VkWallPostGate;

@Service
@Profile("!openapi")
public class ChannelServiceImpl implements ChannelService {

    private final WorkspaceAccessService access;
    private final ChannelRepository channelRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final ChannelConnectStateRepository connectStateRepository;
    private final TelegramBotRepository telegramBotRepository;
    private final PlanLimitService planLimitService;
    private final WorkspaceTokenVault tokenVault;
    private final SocialConnectClient connectClient;
    private final ChannelProperties properties;
    private final VkSocialAccountTokenRefreshService vkTokenRefresh;
    private final AuditRecorder auditRecorder;
    private final OutboxRepository outboxRepository;

    public ChannelServiceImpl(
            WorkspaceAccessService access,
            ChannelRepository channelRepository,
            SocialAccountRepository socialAccountRepository,
            ChannelConnectStateRepository connectStateRepository,
            TelegramBotRepository telegramBotRepository,
            PlanLimitService planLimitService,
            WorkspaceTokenVault tokenVault,
            SocialConnectClient connectClient,
            ChannelProperties properties,
            VkSocialAccountTokenRefreshService vkTokenRefresh,
            AuditRecorder auditRecorder,
            OutboxRepository outboxRepository) {
        this.access = access;
        this.channelRepository = channelRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.connectStateRepository = connectStateRepository;
        this.telegramBotRepository = telegramBotRepository;
        this.planLimitService = planLimitService;
        this.tokenVault = tokenVault;
        this.connectClient = connectClient;
        this.properties = properties;
        this.vkTokenRefresh = vkTokenRefresh;
        this.auditRecorder = auditRecorder;
        this.outboxRepository = outboxRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.ChannelPage list(
            UUID userId,
            UUID workspaceId,
            UUID projectId,
            ApiEnums.Network network,
            ApiEnums.ChannelStatus status,
            CursorPage.Params page) {
        WorkspaceMembership membership = access.requirePermission(workspaceId, userId, "channels.view");
        List<ChannelEntity> channels = channelRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId).stream()
                .filter(c -> projectId == null || projectId.equals(c.getProjectId()))
                .filter(c -> network == null || network.name().equals(c.getNetwork()))
                .filter(c -> status == null || status.name().equals(c.getStatus()))
                .filter(c -> visible(membership, c))
                .sorted(Comparator.comparing(ChannelEntity::getCreatedAt).reversed())
                .toList();
        int limit = page.limitOrDefault();
        List<ChannelDtos.Channel> items =
                channels.stream().limit(limit).map(this::toDto).toList();
        return new CursorPage.ChannelPage(items, null, channels.size() > limit, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelDtos.Channel get(UUID userId, UUID channelId) {
        ChannelEntity channel = requireChannel(channelId);
        WorkspaceMembership membership = access.requirePermission(channel.getWorkspaceId(), userId, "channels.view");
        if (!visible(membership, channel)) {
            throw WorkspaceAccessService.notFound();
        }
        return toDto(channel);
    }

    @Override
    @Transactional
    public ChannelDtos.Channel update(
            UUID userId, UUID channelId, ChannelDtos.UpdateChannelRequest request, ClientRequestContext ctx) {
        ChannelEntity channel = requireChannel(channelId);
        WorkspaceMembership membership = access.requirePermission(channel.getWorkspaceId(), userId, "channels.manage");
        if (request.projectId() != null) {
            access.requireVisibleProject(membership, request.projectId());
            channel.setProjectId(request.projectId());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            channel.setTimezone(request.timezone());
        }
        if (request.threadId() != null) {
            String thread =
                    request.threadId().isBlank() ? null : request.threadId().trim();
            channel.setThreadId(thread);
        }
        if (request.botToken() != null && !request.botToken().isBlank()) {
            if (!"tg".equals(channel.getNetwork()) && !"max".equals(channel.getNetwork())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        ProblemCodes.VALIDATION_FAILED,
                        "Custom bot token is only supported for tg/max channels");
            }
            channel.setBotTokenEnc(
                    tokenVault.seal(channel.getWorkspaceId(), request.botToken().trim()));
        }
        channel.setUpdatedAt(Instant.now());
        channelRepository.save(channel);
        auditRecorder.record(
                channel.getWorkspaceId(),
                userId,
                "channel.updated",
                "channel",
                channel.getId(),
                Map.of("projectId", String.valueOf(channel.getProjectId())),
                ctx.ip());
        return toDto(channel);
    }

    @Override
    @Transactional
    public AuthDtos.MessageResponse delete(UUID userId, UUID channelId, ClientRequestContext ctx) {
        ChannelEntity channel = requireChannel(channelId);
        access.requirePermission(channel.getWorkspaceId(), userId, "channels.manage");
        channel.setStatus("disabled");
        channel.setStatusReason("disconnected_by_user");
        channel.setDeletedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        channelRepository.save(channel);
        auditRecorder.record(
                channel.getWorkspaceId(),
                userId,
                "channel.disconnected",
                "channel",
                channel.getId(),
                Map.of("network", channel.getNetwork(), "externalId", channel.getExternalId()),
                ctx.ip());
        return new AuthDtos.MessageResponse("Channel disconnected");
    }

    @Override
    @Transactional
    public ChannelDtos.ChannelConnectStartResponse connectStart(
            UUID userId, ApiEnums.Network network, UUID workspaceId, UUID projectId, ClientRequestContext ctx) {
        WorkspaceMembership membership = access.requirePermission(workspaceId, userId, "channels.manage");
        if (projectId != null) {
            access.requireVisibleProject(membership, projectId);
        }
        planLimitService.ensureCanAddChannels(workspaceId, 1);

        return switch (network) {
            case vk -> startOAuth(userId, workspaceId, projectId, "vk", true);
            case ok -> startOAuth(userId, workspaceId, projectId, "ok", false);
            case tg -> startBot(userId, workspaceId, projectId, "tg");
            case max -> startBot(userId, workspaceId, projectId, "max");
            case rutube -> throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Rutube connect is phase 2");
        };
    }

    @Override
    @Transactional
    public CursorPage.ChannelPage connectCallback(
            UUID userId,
            ApiEnums.Network network,
            ChannelDtos.ChannelConnectCallbackRequest request,
            ClientRequestContext ctx) {
        if (request.workspaceId() != null) {
            access.requirePermission(request.workspaceId(), userId, "channels.manage");
        } else if (network != ApiEnums.Network.vk && network != ApiEnums.Network.ok) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "workspaceId is required");
        }
        return switch (network) {
            case vk, ok -> finishOAuth(userId, network.name(), request, ctx);
            case tg, max -> finishBot(
                    userId,
                    network.name(),
                    request.workspaceId(),
                    request.projectId(),
                    request.username(),
                    request.chatId(),
                    request.forwardedMessage(),
                    request.botToken(),
                    request.threadId(),
                    ctx);
            case rutube -> throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Rutube connect is phase 2");
        };
    }

    @Override
    @Transactional
    public CursorPage.ChannelPage connectConfirm(
            UUID userId,
            ApiEnums.Network network,
            ChannelDtos.ChannelConnectConfirmRequest request,
            ClientRequestContext ctx) {
        access.requirePermission(request.workspaceId(), userId, "channels.manage");
        if (network != ApiEnums.Network.tg && network != ApiEnums.Network.max) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "confirm is only for tg/max bot flows");
        }
        return finishBot(
                userId,
                network.name(),
                request.workspaceId(),
                request.projectId(),
                request.username(),
                request.chatId(),
                request.forwardedMessage(),
                request.botToken(),
                request.threadId(),
                ctx);
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelDtos.StoryConnection getStoryConnection(UUID userId, UUID channelId) {
        ChannelEntity channel = requireChannel(channelId);
        WorkspaceMembership membership = access.requirePermission(channel.getWorkspaceId(), userId, "channels.view");
        if (!visible(membership, channel)) {
            throw WorkspaceAccessService.notFound();
        }
        return toStoryConnection(channel, null);
    }

    @Override
    @Transactional
    public ChannelDtos.StoryConnection startStoryConnection(
            UUID userId, UUID channelId, ChannelDtos.StartStoryConnectionRequest request, ClientRequestContext ctx) {
        ChannelEntity channel = requireChannel(channelId);
        access.requirePermission(channel.getWorkspaceId(), userId, "channels.manage");
        if (!"vk".equals(channel.getNetwork())) {
            if ("ok".equals(channel.getNetwork())) {
                // OK Moments do not need a separate stories connect — report ready when channel is active.
                if ("active".equals(channel.getStatus())) {
                    channel.setStoryConnectionStatus("ready");
                    channel.setStoryConnectedAt(Instant.now());
                    channel.setStoryConnectionError(null);
                    channel.setUpdatedAt(Instant.now());
                    channelRepository.save(channel);
                }
                auditRecorder.record(
                        channel.getWorkspaceId(),
                        userId,
                        "story.connection.ready",
                        "channel",
                        channelId,
                        Map.of("network", "ok", "note", "no_separate_connect"),
                        ctx == null ? null : ctx.ip());
                return toStoryConnection(channel, null);
            }
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.STORY_UNSUPPORTED_NETWORK,
                    "Story connection is only available for VK (and informational for OK)");
        }

        boolean confirmReady = request != null && Boolean.TRUE.equals(request.confirmReady());
        String authorizationUrl = null;
        if (confirmReady) {
            channel.setStoryConnectionStatus("ready");
            channel.setStoryConnectedAt(Instant.now());
            channel.setStoryConnectionError(null);
            // Reuse group token as story token for local/emulator community tokens with stories scope.
            if (channel.getGroupTokenEnc() != null) {
                channel.setStoryTokenEnc(channel.getGroupTokenEnc());
            }
            auditRecorder.record(
                    channel.getWorkspaceId(),
                    userId,
                    "story.connection.ready",
                    "channel",
                    channelId,
                    Map.of("network", "vk", "mode", "confirmReady"),
                    ctx == null ? null : ctx.ip());
        } else {
            channel.setStoryConnectionStatus("pending");
            channel.setStoryConnectionError(null);
            authorizationUrl = properties.vkAuthorizationUri()
                    + (properties.vkAuthorizationUri().contains("?") ? "&" : "?")
                    + "scope=stories&channel_id="
                    + channelId;
            auditRecorder.record(
                    channel.getWorkspaceId(),
                    userId,
                    "story.connection.started",
                    "channel",
                    channelId,
                    Map.of("network", "vk"),
                    ctx == null ? null : ctx.ip());
        }
        channel.setUpdatedAt(Instant.now());
        channelRepository.save(channel);
        return toStoryConnection(channel, authorizationUrl);
    }

    private ChannelDtos.StoryConnection toStoryConnection(ChannelEntity channel, String authorizationUrl) {
        ApiEnums.StoryConnectionStatus status;
        try {
            status = ApiEnums.StoryConnectionStatus.valueOf(
                    channel.getStoryConnectionStatus() == null ? "missing" : channel.getStoryConnectionStatus());
        } catch (IllegalArgumentException ex) {
            status = ApiEnums.StoryConnectionStatus.missing;
        }
        // OK without explicit flag: treat active channel as ready for Moments.
        if ("ok".equals(channel.getNetwork())
                && status == ApiEnums.StoryConnectionStatus.missing
                && "active".equals(channel.getStatus())) {
            status = ApiEnums.StoryConnectionStatus.ready;
        }
        return new ChannelDtos.StoryConnection(
                channel.getId(),
                ApiEnums.Network.valueOf(channel.getNetwork()),
                status,
                authorizationUrl,
                channel.getStoryConnectedAt(),
                channel.getStoryConnectionError());
    }

    @Override
    @Transactional
    public ChannelDtos.ChannelCheckResponse check(UUID userId, UUID channelId, ClientRequestContext ctx) {
        ChannelEntity channel = requireChannel(channelId);
        access.requirePermission(channel.getWorkspaceId(), userId, "channels.manage");
        String token = resolveAccessToken(channel, true);
        Instant checkedAt = Instant.now();
        boolean ok = token != null && connectClient.probeToken(channel.getNetwork(), token, channel.getExternalId());
        if (ok) {
            channel.setStatus("active");
            channel.setStatusReason(null);
        } else {
            channel.setStatus("reauth_required");
            channel.setStatusReason("token_check_failed");
            if (channel.getSocialAccountId() != null) {
                socialAccountRepository.findById(channel.getSocialAccountId()).ifPresent(account -> {
                    account.setStatus("reauth_required");
                    account.setLastCheckedAt(checkedAt);
                    account.setUpdatedAt(checkedAt);
                    socialAccountRepository.save(account);
                });
            }
        }
        channel.setUpdatedAt(checkedAt);
        channelRepository.save(channel);
        if (!ok) {
            OutboxEntity event = new OutboxEntity();
            event.setAggregate("channel");
            event.setAggregateId(channel.getId());
            event.setEventType("channel.reauth_required");
            event.setPayload(Map.of(
                    "workspaceId", channel.getWorkspaceId().toString(),
                    "channelId", channel.getId().toString(),
                    "network", channel.getNetwork(),
                    "reason", channel.getStatusReason() == null ? "token_check_failed" : channel.getStatusReason()));
            event.setCreatedAt(checkedAt);
            outboxRepository.save(event);
        }
        return new ChannelDtos.ChannelCheckResponse(
                channel.getId(),
                ApiEnums.ChannelStatus.valueOf(channel.getStatus()),
                channel.getStatusReason(),
                checkedAt);
    }

    private ChannelDtos.ChannelConnectStartResponse startOAuth(
            UUID userId, UUID workspaceId, UUID projectId, String network, boolean pkce) {
        String state = Pkce.randomState();
        String verifier = pkce ? Pkce.codeVerifier() : null;
        ChannelConnectStateEntity row = new ChannelConnectStateEntity();
        row.setState(state);
        row.setWorkspaceId(workspaceId);
        row.setProjectId(projectId);
        row.setNetwork(network);
        row.setUserId(userId);
        row.setCodeVerifier(verifier);
        String redirectUri = properties.channelConnectRedirectUri(network, workspaceId);
        row.setRedirectUri(redirectUri);
        row.setCreatedAt(Instant.now());
        row.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        connectStateRepository.save(row);
        String url = network.equals("vk")
                ? connectClient.buildVkAuthorizeUrl(
                        state, verifier == null ? "" : Pkce.codeChallengeS256(verifier), redirectUri)
                : connectClient.buildOkAuthorizeUrl(state, redirectUri);
        return new ChannelDtos.ChannelConnectStartResponse(url, state, null);
    }

    private ChannelDtos.ChannelConnectStartResponse startBot(
            UUID userId, UUID workspaceId, UUID projectId, String network) {
        String state = Pkce.randomState();
        ChannelConnectStateEntity row = new ChannelConnectStateEntity();
        row.setState(state);
        row.setWorkspaceId(workspaceId);
        row.setProjectId(projectId);
        row.setNetwork(network);
        row.setUserId(userId);
        row.setCreatedAt(Instant.now());
        row.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        connectStateRepository.save(row);
        String botUser = properties.tgBotUsername();
        String invite = network.equals("tg")
                ? "https://t.me/" + botUser
                        + "?startchannel&admin=post_messages+edit_messages+delete_messages+pin_messages"
                : "https://max.ru/addbot/" + botUser;
        return new ChannelDtos.ChannelConnectStartResponse(null, state, invite);
    }

    private CursorPage.ChannelPage finishOAuth(
            UUID userId, String network, ChannelDtos.ChannelConnectCallbackRequest request, ClientRequestContext ctx) {
        if (request.code() == null || request.state() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "code and state are required");
        }
        ChannelConnectStateEntity state = connectStateRepository
                .lockByState(request.state())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, ProblemCodes.INVALID_TOKEN, "Invalid or expired connect state"));
        UUID workspaceId = request.workspaceId() != null ? request.workspaceId() : state.getWorkspaceId();
        if (!state.getUserId().equals(userId)
                || !state.getWorkspaceId().equals(workspaceId)
                || !state.getNetwork().equals(network)
                || state.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.INVALID_TOKEN, "Invalid or expired connect state");
        }
        access.requirePermission(workspaceId, userId, "channels.manage");
        if ("vk".equals(network) && properties.vkIdMode()) {
            if (request.deviceId() == null || request.deviceId().isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "device_id is required for VK ID");
            }
        }
        SocialConnectClient.OAuthToken token;
        try {
            token = network.equals("vk")
                    ? connectClient.exchangeVk(
                            request.code(),
                            state.getCodeVerifier(),
                            state.getRedirectUri(),
                            request.deviceId(),
                            request.state())
                    : connectClient.exchangeOk(request.code(), state.getRedirectUri());
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "OAuth token exchange failed");
        }
        connectStateRepository.delete(state);

        List<SocialConnectClient.RemoteChannel> remotes = network.equals("vk")
                ? connectClient.listVkAdminTargets(token.accessToken())
                : connectClient.listOkAdminGroups(token.accessToken());

        List<SocialConnectClient.RemoteChannel> adminRemotes = remotes.stream()
                .filter(SocialConnectClient.RemoteChannel::admin)
                .toList();
        if (adminRemotes.isEmpty()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ProblemCodes.INSUFFICIENT_PERMISSIONS,
                    "No admin groups available. Become an admin/editor of a community and try again.",
                    Map.of("network", network));
        }

        UUID projectId = request.projectId() != null ? request.projectId() : state.getProjectId();
        planLimitService.ensureCanAddChannels(workspaceId, adminRemotes.size());

        SocialAccountEntity account = upsertSocialAccount(
                workspaceId, network, token.externalUserId(), token.displayName(), token, request.deviceId());

        List<ChannelDtos.Channel> created = new ArrayList<>();
        for (SocialConnectClient.RemoteChannel remote : adminRemotes) {
            ChannelEntity channel = upsertChannel(
                    workspaceId,
                    projectId,
                    account.getId(),
                    network,
                    remote,
                    null,
                    remote.groupToken() == null ? null : tokenVault.seal(workspaceId, remote.groupToken()));
            if (VkWallPostGate.cannotPost(token.accessToken(), remote.externalId(), remote.type())) {
                channel.setStatus("no_permissions");
                channel.setStatusReason(VkWallPostGate.BLOCK_MESSAGE);
                channel.setUpdatedAt(Instant.now());
                channel = channelRepository.save(channel);
            }
            created.add(toDto(channel));
            auditRecorder.record(
                    workspaceId,
                    userId,
                    "channel.connected",
                    "channel",
                    channel.getId(),
                    Map.of("network", network, "externalId", remote.externalId()),
                    ctx.ip());
        }
        return new CursorPage.ChannelPage(created, null, false, workspaceId);
    }

    private CursorPage.ChannelPage finishBot(
            UUID userId,
            String network,
            UUID workspaceId,
            UUID projectId,
            String username,
            String chatId,
            ChannelDtos.ForwardedMessageRef forwarded,
            String customBotToken,
            String threadId,
            ClientRequestContext ctx) {
        String ref = firstNonBlank(chatId, username, forwarded == null ? null : forwarded.chatId());
        if (ref == null || ref.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ProblemCodes.VALIDATION_FAILED,
                    "Provide username, chatId, or forwardedMessage.chatId");
        }

        planLimitService.ensureCanAddChannels(workspaceId, 1);

        boolean customBot = customBotToken != null && !customBotToken.isBlank();
        String botToken;
        UUID botId = null;
        String accountExternalId;
        String accountDisplayName;
        if (customBot) {
            botToken = customBotToken.trim();
            accountExternalId = "custom-bot";
            accountDisplayName = "Custom bot";
        } else if ("tg".equals(network)) {
            TelegramBotEntity bot = pickBot();
            botId = bot.getId();
            botToken = resolveBotToken(bot);
            accountExternalId = properties.tgBotUsername();
            accountDisplayName = "@" + properties.tgBotUsername();
        } else {
            botToken = properties.maxBotToken();
            accountExternalId = "max-bot";
            accountDisplayName = "Max bot";
        }

        SocialConnectClient.BotPermissionCheck check = "tg".equals(network)
                ? connectClient.checkTelegram(botToken, ref)
                : connectClient.checkMax(botToken, ref);

        if (!check.ok()) {
            String reason = check.reason() == null ? "insufficient_permissions" : check.reason();
            if ("chat_not_found".equals(reason)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        ProblemCodes.VALIDATION_FAILED,
                        "Bot is not in the chat or username is invalid. Add the bot as admin and retry.");
            }
            throw new ApiException(
                    HttpStatus.FORBIDDEN, ProblemCodes.INSUFFICIENT_PERMISSIONS, reason, Map.of("network", network));
        }

        String externalId = firstNonBlank(chatId, forwarded == null ? null : forwarded.chatId(), ref);
        SocialAccountEntity account = upsertSocialAccount(
                workspaceId,
                network,
                accountExternalId,
                accountDisplayName,
                new SocialConnectClient.OAuthToken(botToken, null, null, "bot", "bot"),
                null);

        SocialConnectClient.RemoteChannel remote = new SocialConnectClient.RemoteChannel(
                externalId,
                check.type() == null ? "channel" : check.type(),
                check.title() == null ? ref : check.title(),
                null,
                check.username() == null ? stripAt(username) : check.username(),
                true,
                null);
        ChannelEntity channel = upsertChannel(workspaceId, projectId, account.getId(), network, remote, botId, null);
        if (customBot) {
            channel.setBotTokenEnc(tokenVault.seal(workspaceId, botToken));
        }
        if (threadId != null && !threadId.isBlank()) {
            channel.setThreadId(threadId.trim());
        }
        channel = channelRepository.save(channel);
        auditRecorder.record(
                workspaceId,
                userId,
                "channel.connected",
                "channel",
                channel.getId(),
                Map.of(
                        "network",
                        network,
                        "externalId",
                        externalId,
                        "customBot",
                        String.valueOf(customBot),
                        "threadId",
                        channel.getThreadId() == null ? "" : channel.getThreadId()),
                ctx.ip());
        return new CursorPage.ChannelPage(List.of(toDto(channel)), null, false, workspaceId);
    }

    private SocialAccountEntity upsertSocialAccount(
            UUID workspaceId,
            String network,
            String externalUserId,
            String displayName,
            SocialConnectClient.OAuthToken token,
            String deviceId) {
        SocialAccountEntity account = socialAccountRepository.findByWorkspaceId(workspaceId).stream()
                .filter(a -> network.equals(a.getNetwork()) && externalUserId.equals(a.getExternalUserId()))
                .findFirst()
                .orElseGet(() -> {
                    SocialAccountEntity created = new SocialAccountEntity();
                    created.setId(UuidV7.generate());
                    created.setWorkspaceId(workspaceId);
                    created.setNetwork(network);
                    created.setExternalUserId(externalUserId);
                    created.setCreatedAt(Instant.now());
                    return created;
                });
        account.setDisplayName(displayName);
        account.setAccessTokenEnc(tokenVault.seal(workspaceId, token.accessToken()));
        if (token.refreshToken() != null) {
            account.setRefreshTokenEnc(tokenVault.seal(workspaceId, token.refreshToken()));
        }
        account.setTokenExpiresAt(token.expiresAt());
        if (deviceId != null && !deviceId.isBlank()) {
            account.setDeviceId(deviceId);
        }
        account.setStatus("active");
        account.setLastCheckedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        applyGrantedScopes(account, token);
        return socialAccountRepository.save(account);
    }

    /** Persist scopes from the token response. A null array means the response omitted {@code scope}. */
    static void applyGrantedScopes(SocialAccountEntity account, SocialConnectClient.OAuthToken token) {
        if (account == null || token == null || token.scopes() == null) {
            return;
        }
        account.setScopes(token.scopes());
    }

    private ChannelEntity upsertChannel(
            UUID workspaceId,
            UUID projectId,
            UUID socialAccountId,
            String network,
            SocialConnectClient.RemoteChannel remote,
            UUID botId,
            byte[] groupTokenEnc) {
        ChannelEntity channel = channelRepository
                .findByWorkspaceIdAndNetworkAndExternalId(workspaceId, network, remote.externalId())
                .orElseGet(() -> {
                    ChannelEntity created = new ChannelEntity();
                    created.setId(UuidV7.generate());
                    created.setWorkspaceId(workspaceId);
                    created.setNetwork(network);
                    created.setExternalId(remote.externalId());
                    created.setCreatedAt(Instant.now());
                    return created;
                });
        channel.setProjectId(projectId);
        channel.setSocialAccountId(socialAccountId);
        channel.setType(remote.type());
        channel.setTitle(remote.title() == null ? remote.externalId() : remote.title());
        channel.setAvatarUrl(remote.avatarUrl());
        channel.setUsername(remote.username());
        channel.setGroupTokenEnc(groupTokenEnc);
        channel.setBotId(botId);
        channel.setStatus("active");
        channel.setStatusReason(null);
        channel.setDeletedAt(null);
        channel.setUpdatedAt(Instant.now());
        channel.setCapabilities(Map.of("network", network));
        return channelRepository.save(channel);
    }

    private TelegramBotEntity pickBot() {
        List<TelegramBotEntity> bots = telegramBotRepository.findByStatusOrderByCreatedAtAsc("active");
        if (bots.isEmpty()) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ProblemCodes.NOT_IMPLEMENTED,
                    "No Telegram bots configured in pool");
        }
        return bots.stream()
                .min(Comparator.comparingLong(b -> channelRepository.countByBotIdAndDeletedAtIsNull(b.getId())))
                .orElse(bots.getFirst());
    }

    private String resolveBotToken(TelegramBotEntity bot) {
        String fromEnv = properties.tgBotToken();
        if (bot.getTokenEnc() == null || bot.getTokenEnc().length <= 1) {
            return fromEnv;
        }
        // Pool bots may carry a sealed token under the zero-UUID workspace key; fall back to env
        // when unwrap is unavailable (local/emulator seed uses 0x00 placeholder).
        try {
            String unsealed = tokenVault.unseal(new UUID(0L, 0L), bot.getTokenEnc());
            if (unsealed != null && !unsealed.isBlank()) {
                return unsealed;
            }
        } catch (RuntimeException ignored) {
            // env fallback
        }
        return fromEnv;
    }

    private String resolveAccessToken(ChannelEntity channel, boolean refreshIfStale) {
        if (channel.getSocialAccountId() == null) {
            return null;
        }
        return socialAccountRepository
                .findById(channel.getSocialAccountId())
                .map(account -> {
                    SocialAccountEntity fresh = refreshIfStale ? vkTokenRefresh.ensureFresh(account) : account;
                    return tokenVault.unseal(channel.getWorkspaceId(), fresh.getAccessTokenEnc());
                })
                .orElse(null);
    }

    /** Fail-open token read for channel list overlay. Does not refresh or write. */
    private String tryResolveAccessToken(ChannelEntity channel) {
        if (channel.getSocialAccountId() == null) {
            return null;
        }
        return socialAccountRepository
                .findById(channel.getSocialAccountId())
                .flatMap(account -> tokenVault.tryUnseal(channel.getWorkspaceId(), account.getAccessTokenEnc()))
                .orElse(null);
    }

    /**
     * Reject scheduling onto a VK target whose token cannot call {@code wall.post}. Persists
     * {@code no_permissions} so the channel list keeps showing the reason.
     */
    public void assertVkWallPostAllowed(ChannelEntity channel) {
        if (channel == null || !"vk".equals(channel.getNetwork())) {
            return;
        }
        if (VkWallPostGate.blockedByPriorDenial(channel.getStatus())) {
            String reason = channel.getStatusReason();
            if (reason == null || reason.isBlank()) {
                reason = VkWallPostGate.BLOCK_MESSAGE;
                channel.setStatusReason(reason);
                channel.setUpdatedAt(Instant.now());
                channelRepository.save(channel);
            }
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.VALIDATION_FAILED, reason);
        }
        if (!vkProfileTokenCannotPost(channel)) {
            return;
        }
        channel.setStatus("no_permissions");
        channel.setStatusReason(VkWallPostGate.BLOCK_MESSAGE);
        channel.setUpdatedAt(Instant.now());
        channelRepository.save(channel);
        throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.VALIDATION_FAILED, VkWallPostGate.BLOCK_MESSAGE);
    }

    private boolean vkProfileTokenCannotPost(ChannelEntity channel) {
        if (channel == null || !"vk".equals(channel.getNetwork())) {
            return false;
        }
        if (!"active".equals(channel.getStatus())) {
            return false;
        }
        if (!VkWallPostGate.isPersonalProfile(channel.getExternalId(), channel.getType())) {
            return false;
        }
        return VkWallPostGate.cannotPost(tryResolveAccessToken(channel), channel.getExternalId(), channel.getType());
    }

    private ChannelEntity requireChannel(UUID channelId) {
        return channelRepository.findByIdAndDeletedAtIsNull(channelId).orElseThrow(WorkspaceAccessService::notFound);
    }

    private boolean visible(WorkspaceMembership membership, ChannelEntity channel) {
        if (membership.role().seesAllProjects()) {
            return true;
        }
        if (channel.getProjectId() == null) {
            return true;
        }
        return membership.assignedProjectIds().contains(channel.getProjectId());
    }

    private ChannelDtos.Channel toDto(ChannelEntity channel) {
        String status = channel.getStatus();
        String statusReason = channel.getStatusReason();
        if (vkProfileTokenCannotPost(channel)) {
            status = "no_permissions";
            statusReason = VkWallPostGate.BLOCK_MESSAGE;
        }
        ApiEnums.StoryConnectionStatus storyStatus;
        try {
            storyStatus = ApiEnums.StoryConnectionStatus.valueOf(
                    channel.getStoryConnectionStatus() == null ? "missing" : channel.getStoryConnectionStatus());
        } catch (IllegalArgumentException ex) {
            storyStatus = ApiEnums.StoryConnectionStatus.missing;
        }
        return new ChannelDtos.Channel(
                channel.getId(),
                channel.getWorkspaceId(),
                channel.getProjectId(),
                ApiEnums.Network.valueOf(channel.getNetwork()),
                channel.getExternalId(),
                ApiEnums.ChannelType.valueOf(channel.getType()),
                channel.getTitle(),
                channel.getAvatarUrl(),
                channel.getUsername(),
                ApiEnums.ChannelStatus.valueOf(status),
                statusReason,
                channel.getTimezone(),
                channel.getCapabilities(),
                channel.getCreatedAt(),
                storyStatus,
                channel.getStoryConnectedAt(),
                channel.getStoryConnectionError(),
                channel.getThreadId(),
                channel.getBotTokenEnc() != null && channel.getBotTokenEnc().length > 1);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String stripAt(String username) {
        if (username == null) {
            return null;
        }
        return username.startsWith("@") ? username.substring(1) : username;
    }
}
