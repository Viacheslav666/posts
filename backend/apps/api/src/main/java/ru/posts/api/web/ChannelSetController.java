package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.auth.ClientRequestContexts;
import ru.posts.api.channel.ChannelSetService;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.channel.ChannelDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@Validated
@RequestMapping(path = "/api/v1/channel-sets", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Channel sets", description = "Named channel groups for one-click multi-publish (US-3.6)")
@OpenApiResponses
public class ChannelSetController {

    private final ChannelSetService channelSetService;

    public ChannelSetController(ChannelSetService channelSetService) {
        this.channelSetService = channelSetService;
    }

    @GetMapping
    @Operation(operationId = "channelSetsList", summary = "List channel sets in a workspace")
    @ApiResponse(responseCode = "200", description = "Channel sets page")
    public CursorPage.ChannelSetPage list(
            @RequestParam UUID workspaceId, @Valid @ParameterObject CursorPage.Params page) {
        return channelSetService.list(SecurityAuthSupport.requirePrincipal().userId(), workspaceId, page);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "channelSetsCreate", summary = "Create a channel set (max 50 channels)")
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(implementation = ChannelDtos.ChannelSet.class)))
    public ChannelDtos.ChannelSet create(
            @RequestParam UUID workspaceId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ChannelDtos.CreateChannelSetRequest request,
            HttpServletRequest httpRequest) {
        return channelSetService.create(
                SecurityAuthSupport.requirePrincipal().userId(),
                workspaceId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/{setId}")
    @Operation(operationId = "channelSetsGet", summary = "Get channel set by id")
    @ApiResponse(responseCode = "200", description = "Channel set")
    public ChannelDtos.ChannelSet get(@PathVariable UUID setId) {
        return channelSetService.get(SecurityAuthSupport.requirePrincipal().userId(), setId);
    }

    @PutMapping("/{setId}")
    @Operation(operationId = "channelSetsUpdate", summary = "Update channel set name or membership")
    @ApiResponse(responseCode = "200", description = "Updated")
    public ChannelDtos.ChannelSet update(
            @PathVariable UUID setId,
            @Valid @RequestBody ChannelDtos.UpdateChannelSetRequest request,
            HttpServletRequest httpRequest) {
        return channelSetService.update(
                SecurityAuthSupport.requirePrincipal().userId(),
                setId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @DeleteMapping("/{setId}")
    @Operation(operationId = "channelSetsDelete", summary = "Delete a channel set")
    @ApiResponse(responseCode = "200", description = "Deleted")
    public AuthDtos.MessageResponse delete(@PathVariable UUID setId, HttpServletRequest httpRequest) {
        return channelSetService.delete(
                SecurityAuthSupport.requirePrincipal().userId(), setId, ClientRequestContexts.from(httpRequest));
    }
}
