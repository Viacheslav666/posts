package ru.posts.api.web.dto.media;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.posts.api.web.dto.common.ApiEnums;

public final class MediaDtos {

    private MediaDtos() {}

    @Schema(name = "MediaAsset")
    public record MediaAsset(
            UUID id,
            UUID workspaceId,
            UUID uploaderId,
            ApiEnums.MediaKind kind,
            String mime,
            long sizeBytes,
            Integer width,
            Integer height,
            Integer durationMs,
            String sha256,
            ApiEnums.MediaStatus status,
            Map<String, Object> derivatives,
            Instant createdAt) {}

    @Schema(name = "CreateUploadRequest")
    public record CreateUploadRequest(
            @NotNull UUID workspaceId,
            @NotNull ApiEnums.MediaKind kind,
            @NotBlank @Schema(example = "image/jpeg") String mime,
            @NotNull @Min(1) Long sizeBytes,
            @Size(min = 64, max = 64) @Schema(description = "SHA-256 hex of the file") String sha256,
            String filename) {}

    @Schema(name = "CreateUploadResponse")
    public record CreateUploadResponse(
            UUID mediaId,
            @Schema(description = "Presigned multipart/PUT upload URL") String uploadUrl,
            Map<String, String> headers,
            Instant expiresAt) {}

    @Schema(name = "CompleteUploadRequest")
    public record CompleteUploadRequest(
            @Schema(description = "S3 multipart upload id when applicable") String uploadId, List<String> etags) {}
}
