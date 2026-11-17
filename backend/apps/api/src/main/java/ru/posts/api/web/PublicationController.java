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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.post.PostDtos;
import ru.posts.api.web.support.ApiStub;

@RestController
@Validated
@RequestMapping(path = "/api/v1/publications", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Publications", description = "Per-channel publication attempts and live status")
@OpenApiResponses
public class PublicationController {

    @GetMapping
    @Operation(operationId = "publicationsList", summary = "List publications for a workspace")
    @ApiResponse(responseCode = "200", description = "Publications page")
    public CursorPage.Response<PostDtos.Publication> list(
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) UUID postId,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(required = false) ApiEnums.PublicationState state,
            @RequestParam(required = false) ApiEnums.Network network,
            @Valid @ParameterObject CursorPage.Params page) {
        throw ApiStub.notImplemented("publicationsList");
    }

    @GetMapping("/{publicationId}")
    @Operation(operationId = "publicationsGet", summary = "Get a publication by id")
    @ApiResponse(
            responseCode = "200",
            description = "Publication",
            content = @Content(schema = @Schema(implementation = PostDtos.Publication.class)))
    public PostDtos.Publication get(@PathVariable UUID publicationId) {
        throw ApiStub.notImplemented("publicationsGet");
    }
}
