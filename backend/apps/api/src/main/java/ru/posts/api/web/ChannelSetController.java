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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.channel.ChannelDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.support.ApiStub;

@RestController
@Validated
@RequestMapping(path = "/api/v1/channel-sets", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Channel sets", description = "Named channel groups for one-click multi-publish (US-3.6)")
@OpenApiResponses
public class ChannelSetController {

    @GetMapping
    @Operation(operationId = "channelSetsList", summary = "List channel sets in a workspace")
    @ApiResponse(responseCode = "200", description = "Channel sets page")
    public CursorPage.Response<ChannelDtos.ChannelSet> list(
            @RequestParam UUID workspaceId, @Valid @ParameterObject CursorPage.Params page) {
        throw ApiStub.notImplemented("channelSetsList");
    }

    @PostMapping
    @Operation(operationId = "channelSetsCreate", summary = "Create a channel set (max 50 channels)")
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(implementation = ChannelDtos.ChannelSet.class)))
    public ChannelDtos.ChannelSet create(
            @RequestParam UUID workspaceId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ChannelDtos.CreateChannelSetRequest request) {
        throw ApiStub.notImplemented("channelSetsCreate");
    }

    @GetMapping("/{setId}")
    @Operation(operationId = "channelSetsGet", summary = "Get channel set by id")
    @ApiResponse(responseCode = "200", description = "Channel set")
    public ChannelDtos.ChannelSet get(@PathVariable UUID setId) {
        throw ApiStub.notImplemented("channelSetsGet");
    }

    @PutMapping("/{setId}")
    @Operation(operationId = "channelSetsUpdate", summary = "Update channel set name or membership")
    @ApiResponse(responseCode = "200", description = "Updated")
    public ChannelDtos.ChannelSet update(
            @PathVariable UUID setId, @Valid @RequestBody ChannelDtos.UpdateChannelSetRequest request) {
        throw ApiStub.notImplemented("channelSetsUpdate");
    }

    @DeleteMapping("/{setId}")
    @Operation(operationId = "channelSetsDelete", summary = "Delete a channel set")
    @ApiResponse(responseCode = "200", description = "Deleted")
    public AuthDtos.MessageResponse delete(@PathVariable UUID setId) {
        throw ApiStub.notImplemented("channelSetsDelete");
    }
}
