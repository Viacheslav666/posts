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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.media.MediaDtos;
import ru.posts.api.web.support.ApiStub;

@RestController
@Validated
@RequestMapping(path = "/api/v1/media", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Media", description = "Media library uploads via presigned S3 URLs")
@OpenApiResponses
public class MediaController {

    @GetMapping
    @Operation(operationId = "mediaList", summary = "List media assets in a workspace")
    @ApiResponse(responseCode = "200", description = "Media page")
    public CursorPage.Response<MediaDtos.MediaAsset> list(
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) ApiEnums.MediaKind kind,
            @RequestParam(required = false) ApiEnums.MediaStatus status,
            @Valid @ParameterObject CursorPage.Params page) {
        throw ApiStub.notImplemented("mediaList");
    }

    @GetMapping("/{mediaId}")
    @Operation(operationId = "mediaGet", summary = "Get media asset metadata")
    @ApiResponse(
            responseCode = "200",
            description = "Media asset",
            content = @Content(schema = @Schema(implementation = MediaDtos.MediaAsset.class)))
    public MediaDtos.MediaAsset get(@PathVariable UUID mediaId) {
        throw ApiStub.notImplemented("mediaGet");
    }

    @PostMapping("/uploads")
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
            @Valid @RequestBody MediaDtos.CreateUploadRequest request) {
        throw ApiStub.notImplemented("mediaCreateUpload");
    }

    @PostMapping("/{mediaId}/complete")
    @Operation(operationId = "mediaCompleteUpload", summary = "Mark upload complete and enqueue processing")
    @ApiResponse(
            responseCode = "200",
            description = "Media asset",
            content = @Content(schema = @Schema(implementation = MediaDtos.MediaAsset.class)))
    public MediaDtos.MediaAsset complete(
            @PathVariable UUID mediaId, @Valid @RequestBody(required = false) MediaDtos.CompleteUploadRequest request) {
        throw ApiStub.notImplemented("mediaCompleteUpload");
    }
}
