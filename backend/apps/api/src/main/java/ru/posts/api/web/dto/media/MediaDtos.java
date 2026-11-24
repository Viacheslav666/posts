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

    @Schema(name = "MediaUsage")
    public record MediaUsage(UUID postId, String title, ApiEnums.PostStatus status) {}

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
            Instant createdAt,
            String filename,
            String folder,
            List<String> tags,
            String errorMessage,
            List<MediaUsage> usedIn,
            @Schema(description = "Presigned GET URL for the card preview. Null when none applies or presign failed.")
                    String previewUrl) {}

    @Schema(name = "CreateUploadRequest")
    public record CreateUploadRequest(
            @NotNull UUID workspaceId,
            @NotNull ApiEnums.MediaKind kind,
            @NotBlank @Schema(example = "image/jpeg") String mime,
            @NotNull @Min(1) Long sizeBytes,
            @Size(min = 64, max = 64) @Schema(description = "SHA-256 hex of the file") String sha256,
            String filename,
            String folder,
            List<String> tags) {}

    @Schema(name = "UploadPart")
    public record UploadPart(
            @Schema(description = "1-based S3 part number") int partNumber,
            @Schema(
                            description =
                                    "Presigned UploadPart URL. PUT the part bytes to this URL exactly; do not add query parameters.")
                    String url) {}

    @Schema(name = "CreateUploadResponse")
    public record CreateUploadResponse(
            UUID mediaId,
            @Schema(
                            description =
                                    "Presigned PUT URL. For a single upload (uploadId null) PUT the whole file to this URL unchanged. For multipart, prefer parts[].url.")
                    String uploadUrl,
            Map<String, String> headers,
            Instant expiresAt,
            @Schema(description = "S3 multipart upload id. Null for a single presigned PUT.") String uploadId,
            @Schema(description = "One presigned URL per part when multipart is used. Empty for a single PUT.")
                    List<UploadPart> parts) {}

    @Schema(name = "MediaFolder")
    public record MediaFolder(String id, String name, UUID workspaceId) {}

    @Schema(name = "MediaFolderList")
    public record MediaFolderList(List<MediaFolder> items) {}

    @Schema(name = "MediaQuota")
    public record MediaQuota(UUID workspaceId, long usedBytes, long limitBytes, long usedCount, long limitCount) {}

    @Schema(name = "CompleteUploadRequest")
    public record CompleteUploadRequest(
            @Schema(description = "S3 multipart upload id when applicable") String uploadId, List<String> etags) {}

    @Schema(name = "ImportUrlRequest")
    public record ImportUrlRequest(
            @NotNull UUID workspaceId,
            @NotBlank @Schema(example = "https://cdn.example.com/photo.jpg") String url,
            String filename) {}

    @Schema(name = "ImportUrlResponse")
    public record ImportUrlResponse(UUID mediaId, ApiEnums.MediaStatus status) {}
}
