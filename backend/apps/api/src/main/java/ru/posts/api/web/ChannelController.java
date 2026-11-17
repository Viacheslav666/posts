package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.channel.ChannelDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.support.ApiStub;

@RestController
@Validated
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Channels", description = "Connected social channels and connect flows")
@OpenApiResponses
public class ChannelController {

    @GetMapping("/channels")
    @Operation(operationId = "channelsList", summary = "List channels in a workspace")
    @ApiResponse(responseCode = "200", description = "Channels page")
    public CursorPage.ChannelPage list(
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) ApiEnums.Network network,
            @RequestParam(required = false) ApiEnums.ChannelStatus status,
            @Valid @ParameterObject CursorPage.Params page) {
        throw ApiStub.notImplemented("channelsList");
    }

    @GetMapping("/channels/{channelId}")
    @Operation(operationId = "channelsGet", summary = "Get channel by id")
    @ApiResponse(
            responseCode = "200",
            description = "Channel",
            content = @Content(schema = @Schema(implementation = ChannelDtos.Channel.class)))
    public ChannelDtos.Channel get(@PathVariable UUID channelId) {
        throw ApiStub.notImplemented("channelsGet");
    }

    @PutMapping("/channels/{channelId}")
    @Operation(operationId = "channelsUpdate", summary = "Update channel project assignment or timezone")
    @ApiResponse(responseCode = "200", description = "Updated")
    public ChannelDtos.Channel update(
            @PathVariable UUID channelId, @Valid @RequestBody ChannelDtos.UpdateChannelRequest request) {
        throw ApiStub.notImplemented("channelsUpdate");
    }

    @DeleteMapping("/channels/{channelId}")
    @Operation(operationId = "channelsDelete", summary = "Disconnect a channel")
    @ApiResponse(responseCode = "200", description = "Disconnected")
    public AuthDtos.MessageResponse delete(@PathVariable UUID channelId) {
        throw ApiStub.notImplemented("channelsDelete");
    }

    @PostMapping("/channels/connect/{network}/start")
    @Operation(operationId = "channelsConnectStart", summary = "Start channel connect OAuth / bot invite flow")
    @ApiResponse(
            responseCode = "200",
            description = "Connect start payload",
            content = @Content(schema = @Schema(implementation = ChannelDtos.ChannelConnectStartResponse.class)))
    public ChannelDtos.ChannelConnectStartResponse connectStart(
            @PathVariable ApiEnums.Network network,
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) UUID projectId) {
        throw ApiStub.notImplemented("channelsConnectStart");
    }

    @PostMapping("/channels/connect/{network}/callback")
    @Operation(
            operationId = "channelsConnectCallback",
            summary = "Finish channel connect after OAuth or bot confirmation",
            description = "OAuth: code+state. Bot (tg/max): username and/or forwardedMessage/chatId. "
                    + "Missing bot rights → 403 `insufficient_permissions`.")
    @ApiResponse(
            responseCode = "200",
            description = "Connected channel(s)",
            content = @Content(schema = @Schema(implementation = CursorPage.ChannelPage.class)))
    public CursorPage.ChannelPage connectCallback(
            @PathVariable ApiEnums.Network network,
            @Valid @RequestBody ChannelDtos.ChannelConnectCallbackRequest request) {
        throw ApiStub.notImplemented("channelsConnectCallback");
    }

    @PostMapping("/channels/connect/{network}/confirm")
    @Operation(
            operationId = "channelsConnectConfirm",
            summary = "Confirm Telegram/Max bot channel attach (US-3.2 / US-3.4)",
            description = "After the bot is added as admin, user submits @username or a forwarded message. "
                    + "Permission check failure → ProblemDetail.code=`insufficient_permissions`.")
    @ApiResponse(
            responseCode = "200",
            description = "Connected channel(s)",
            content = @Content(schema = @Schema(implementation = CursorPage.ChannelPage.class)))
    public CursorPage.ChannelPage connectConfirm(
            @PathVariable ApiEnums.Network network,
            @Valid @RequestBody ChannelDtos.ChannelConnectConfirmRequest request) {
        throw ApiStub.notImplemented("channelsConnectConfirm");
    }

    @PostMapping("/channels/{channelId}/check")
    @Operation(operationId = "channelsCheck", summary = "Re-check channel token and permissions (US-3.5)")
    @ApiResponse(
            responseCode = "200",
            description = "Check result",
            content = @Content(schema = @Schema(implementation = ChannelDtos.ChannelCheckResponse.class)))
    public ChannelDtos.ChannelCheckResponse check(@PathVariable UUID channelId) {
        throw ApiStub.notImplemented("channelsCheck");
    }
}
