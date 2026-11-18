package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.auth.ClientRequestContexts;
import ru.posts.api.post.PublicationService;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.post.PostDtos;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@Validated
@RequestMapping(path = "/api/v1/publications", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Publications", description = "Per-channel publication attempts and live status")
@OpenApiResponses
public class PublicationController {

    private final PublicationService publicationService;

    public PublicationController(PublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @GetMapping
    @Operation(operationId = "publicationsList", summary = "List publications for a workspace")
    @ApiResponse(responseCode = "200", description = "Publications page")
    public CursorPage.PublicationPage list(
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) UUID postId,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(required = false) ApiEnums.PublicationState state,
            @RequestParam(required = false) ApiEnums.Network network,
            @Valid @ParameterObject CursorPage.Params page) {
        return publicationService.list(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, postId, channelId, state, network, page);
    }

    @GetMapping("/{publicationId}")
    @Operation(operationId = "publicationsGet", summary = "Get a publication by id")
    @ApiResponse(
            responseCode = "200",
            description = "Publication",
            content = @Content(schema = @Schema(implementation = PostDtos.Publication.class)))
    public PostDtos.Publication get(@PathVariable UUID publicationId) {
        return publicationService.get(SecurityAuthSupport.requirePrincipal().userId(), publicationId);
    }

    @PostMapping("/{publicationId}/retry")
    @Operation(
            operationId = "publicationsRetry",
            summary = "Manually retry a failed publication (US-5.2)",
            description =
                    "Requires `posts.retry_publish`. Moves `failed_retryable` / selected permanent failures back to pending.")
    @ApiResponse(
            responseCode = "200",
            description = "Publication queued for retry",
            content = @Content(schema = @Schema(implementation = PostDtos.Publication.class)))
    public PostDtos.Publication retry(@PathVariable UUID publicationId, HttpServletRequest httpRequest) {
        return publicationService.retry(
                SecurityAuthSupport.requirePrincipal().userId(),
                publicationId,
                ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/{publicationId}/attempts")
    @Operation(
            operationId = "publicationsListAttempts",
            summary = "List publication attempt timeline (FE-8)",
            description = "Chronology of publish attempts for UI history.")
    @ApiResponse(
            responseCode = "200",
            description = "Attempts",
            content =
                    @Content(
                            array = @ArraySchema(schema = @Schema(implementation = PostDtos.PublicationAttempt.class))))
    public List<PostDtos.PublicationAttempt> listAttempts(@PathVariable UUID publicationId) {
        return publicationService.listAttempts(
                SecurityAuthSupport.requirePrincipal().userId(), publicationId);
    }
}
