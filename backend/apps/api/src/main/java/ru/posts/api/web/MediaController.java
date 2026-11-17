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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.auth.ClientRequestContexts;
import ru.posts.api.media.MediaService;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.media.MediaDtos;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@Validated
@RequestMapping(path = "/api/v1/media", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Media", description = "Media library uploads via presigned S3 URLs")
@OpenApiResponses
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping
    @Operation(operationId = "mediaList", summary = "List media assets in a workspace")
    @ApiResponse(responseCode = "200", description = "Media page")
    public CursorPage.MediaPage list(
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) ApiEnums.MediaKind kind,
            @RequestParam(required = false) ApiEnums.MediaStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String folder,
            @RequestParam(required = false) String tag,
            @Valid @ParameterObject CursorPage.Params page) {
        return mediaService.list(
                SecurityAuthSupport.requirePrincipal().userId(), workspaceId, kind, status, q, folder, tag, page);
    }

    @PostMapping("/import-url")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "mediaImportUrl", summary = "Import a remote media URL into the library")
    @ApiResponse(
            responseCode = "201",
            description = "Import started",
            content = @Content(schema = @Schema(implementation = MediaDtos.ImportUrlResponse.class)))
    public MediaDtos.ImportUrlResponse importUrl(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MediaDtos.ImportUrlRequest request,
            HttpServletRequest httpRequest) {
        return mediaService.importUrl(
                SecurityAuthSupport.requirePrincipal().userId(),
                request,
                idempotencyKey,
                ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/{mediaId}")
    @Operation(operationId = "mediaGet", summary = "Get media asset metadata")
    @ApiResponse(
            responseCode = "200",
            description = "Media asset",
            content = @Content(schema = @Schema(implementation = MediaDtos.MediaAsset.class)))
    public MediaDtos.MediaAsset get(@PathVariable UUID mediaId) {
        return mediaService.get(SecurityAuthSupport.requirePrincipal().userId(), mediaId);
    }

    @DeleteMapping("/{mediaId}")
    @Operation(operationId = "mediaDelete", summary = "Soft-delete a media asset")
    @ApiResponse(
            responseCode = "200",
            description = "Deleted",
            content = @Content(schema = @Schema(implementation = AuthDtos.MessageResponse.class)))
    public AuthDtos.MessageResponse delete(@PathVariable UUID mediaId, HttpServletRequest httpRequest) {
        return mediaService.delete(
                SecurityAuthSupport.requirePrincipal().userId(), mediaId, ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "mediaCreateUpload",
            summary = "Create a media asset and return a presigned upload URL",
            description = "Client uploads bytes directly to S3, then calls complete (US-4.2).")
    @ApiResponse(
            responseCode = "201",
            description = "Upload session created",
            content = @Content(schema = @Schema(implementation = MediaDtos.CreateUploadResponse.class)))
    public MediaDtos.CreateUploadResponse createUpload(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MediaDtos.CreateUploadRequest request,
            HttpServletRequest httpRequest) {
        return mediaService.createUpload(
                SecurityAuthSupport.requirePrincipal().userId(),
                request,
                idempotencyKey,
                ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/{mediaId}/complete")
    @Operation(operationId = "mediaCompleteUpload", summary = "Mark upload complete and enqueue processing")
    @ApiResponse(
            responseCode = "200",
            description = "Media asset",
            content = @Content(schema = @Schema(implementation = MediaDtos.MediaAsset.class)))
    public MediaDtos.MediaAsset complete(
            @PathVariable UUID mediaId,
            @Valid @RequestBody(required = false) MediaDtos.CompleteUploadRequest request,
            HttpServletRequest httpRequest) {
        return mediaService.complete(
                SecurityAuthSupport.requirePrincipal().userId(),
                mediaId,
                request,
                ClientRequestContexts.from(httpRequest));
    }
}
