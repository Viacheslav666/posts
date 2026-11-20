package ru.posts.api.media;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.config.S3Properties;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.idempotency.IdempotencyService;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.media.MediaDtos;
import ru.posts.api.web.support.CursorPages;
import ru.posts.api.workspace.AuditRecorder;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.common.s3.S3PresignUrls;
import ru.posts.messaging.media.MediaProcessCommand;
import ru.posts.messaging.topology.MediaTopology;
import ru.posts.persistence.entity.MediaAssetEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.MediaAssetRepository;
import ru.posts.persistence.repository.PlanRepository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

@Service
@Profile("!openapi")
@Transactional
public class MediaServiceImpl implements MediaService {

    private static final long MULTIPART_THRESHOLD_BYTES = 5L * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final long MAX_IMPORT_BYTES = 1024L * 1024 * 1024;
    static final String IDEMPOTENCY_SCOPE_UPLOAD = "POST /api/v1/media/uploads";
    static final String IDEMPOTENCY_SCOPE_IMPORT = "POST /api/v1/media/import-url";

    private final MediaAssetRepository mediaAssetRepository;
    private final PlanRepository planRepository;
    private final WorkspaceAccessService access;
    private final AuditRecorder auditRecorder;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final ObjectProvider<RabbitTemplate> rabbitTemplate;
    private final IdempotencyService idempotencyService;
    private final TransactionTemplate writeTx;
    private final JdbcClient jdbcClient;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public MediaServiceImpl(
            MediaAssetRepository mediaAssetRepository,
            PlanRepository planRepository,
            WorkspaceAccessService access,
            AuditRecorder auditRecorder,
            S3Client s3Client,
            S3Presigner s3Presigner,
            S3Properties s3Properties,
            ObjectProvider<RabbitTemplate> rabbitTemplate,
            IdempotencyService idempotencyService,
            PlatformTransactionManager transactionManager,
            javax.sql.DataSource dataSource) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.planRepository = planRepository;
        this.access = access;
        this.auditRecorder = auditRecorder;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.s3Properties = s3Properties;
        this.rabbitTemplate = rabbitTemplate;
        this.idempotencyService = idempotencyService;
        this.writeTx = new TransactionTemplate(transactionManager);
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.MediaPage list(
            UUID userId,
            UUID workspaceId,
            ApiEnums.MediaKind kind,
            ApiEnums.MediaStatus status,
            String q,
            String folder,
            String tag,
            CursorPage.Params page) {
        access.requirePermission(workspaceId, userId, "media.view");
        List<MediaAssetEntity> found = mediaAssetRepository.search(
                workspaceId,
                kind == null ? null : kind.name(),
                status == null ? null : status.name(),
                blankToNull(folder),
                blankToNull(tag),
                blankToNull(q));
        List<MediaAssetEntity> ordered = found.stream()
                .sorted(Comparator.comparing(MediaAssetEntity::getId).reversed())
                .toList();
        CursorPages.Slice<MediaAssetEntity> slice =
                CursorPages.slice(ordered, page.cursor(), page.limitOrDefault(), MediaAssetEntity::getId);
        List<MediaDtos.MediaAsset> items =
                slice.items().stream().map(m -> toDto(m, List.of())).toList();
        return new CursorPage.MediaPage(items, slice.nextCursor(), slice.hasMore());
    }

    @Override
    @Transactional(readOnly = true)
    public MediaDtos.MediaAsset get(UUID userId, UUID mediaId) {
        MediaAssetEntity asset = requireMedia(mediaId);
        access.requirePermission(asset.getWorkspaceId(), userId, "media.view");
        return toDto(asset, findUsages(asset.getId()));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MediaDtos.CreateUploadResponse createUpload(
            UUID userId, MediaDtos.CreateUploadRequest request, String idempotencyKey, ClientRequestContext ctx) {
        return idempotencyService.execute(
                userId,
                idempotencyKey,
                IDEMPOTENCY_SCOPE_UPLOAD,
                request,
                MediaDtos.CreateUploadResponse.class,
                () -> writeTx.execute(status -> createUploadOnce(userId, request, ctx)));
    }

    private MediaDtos.CreateUploadResponse createUploadOnce(
            UUID userId, MediaDtos.CreateUploadRequest request, ClientRequestContext ctx) {
        access.requirePermission(request.workspaceId(), userId, "media.manage");
        purgeStaleUploadingAssets(request.workspaceId());
        ensureStorageQuota(request.workspaceId(), request.sizeBytes());

        String filename = sanitizeFilename(request.filename(), request.kind(), request.mime());
        MediaAssetEntity asset = new MediaAssetEntity();
        asset.setWorkspaceId(request.workspaceId());
        asset.setUploaderId(userId);
        asset.setKind(request.kind().name());
        asset.setMime(request.mime());
        asset.setSizeBytes(request.sizeBytes());
        asset.setSha256(request.sha256());
        asset.setFilename(filename);
        asset.setFolder(blankToNull(request.folder()));
        asset.setTags(request.tags() == null ? List.of() : List.copyOf(request.tags()));
        asset.setStatus("uploading");
        asset.setDerivatives(Map.of());
        asset.setMeta(Map.of());
        asset.setCreatedAt(Instant.now());
        asset.setUpdatedAt(Instant.now());
        // temporary key until id assigned
        asset.setS3Key("pending");
        asset = mediaAssetRepository.save(asset);

        String s3Key = "workspaces/" + request.workspaceId() + "/media/" + asset.getId() + "/" + filename;
        asset.setS3Key(s3Key);
        asset = mediaAssetRepository.save(asset);

        Instant expiresAt = Instant.now().plus(s3Properties.getPresignTtl());
        String uploadId = null;
        String uploadUrl;
        Map<String, String> headers = new LinkedHashMap<>();

        try {
            if (request.sizeBytes() >= MULTIPART_THRESHOLD_BYTES) {
                var created = s3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                        .bucket(s3Properties.getBucket())
                        .key(s3Key)
                        .contentType(request.mime())
                        .build());
                uploadId = created.uploadId();
                PresignedUploadPartRequest presigned = s3Presigner.presignUploadPart(UploadPartPresignRequest.builder()
                        .signatureDuration(s3Properties.getPresignTtl())
                        .uploadPartRequest(UploadPartRequest.builder()
                                .bucket(s3Properties.getBucket())
                                .key(s3Key)
                                .uploadId(uploadId)
                                .partNumber(1)
                                .build())
                        .build());
                uploadUrl = presigned.url().toString();
                headers.put("Content-Type", request.mime());
            } else {
                PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(s3Properties.getPresignTtl())
                        .putObjectRequest(PutObjectRequest.builder()
                                .bucket(s3Properties.getBucket())
                                .key(s3Key)
                                .contentType(request.mime())
                                .build())
                        .build());
                uploadUrl = presigned.url().toString();
                headers.put("Content-Type", request.mime());
            }
        } catch (software.amazon.awssdk.core.exception.SdkClientException
                | software.amazon.awssdk.services.s3.model.S3Exception ex) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, ProblemCodes.CONFLICT, "Object storage is unavailable");
        }
        uploadUrl = S3PresignUrls.toPublicUrl(uploadUrl, s3Properties.getPresignPublicBaseUrl());

        auditRecorder.record(
                request.workspaceId(),
                userId,
                "media.upload.create",
                "media_asset",
                asset.getId(),
                Map.of("filename", filename, "sizeBytes", request.sizeBytes()),
                ctx == null ? null : ctx.ip());

        return new MediaDtos.CreateUploadResponse(asset.getId(), uploadUrl, headers, expiresAt, uploadId);
    }

    @Override
    public MediaDtos.MediaAsset complete(
            UUID userId, UUID mediaId, MediaDtos.CompleteUploadRequest request, ClientRequestContext ctx) {
        MediaAssetEntity asset = requireMedia(mediaId);
        access.requirePermission(asset.getWorkspaceId(), userId, "media.manage");

        if (request != null && request.uploadId() != null && !request.uploadId().isBlank()) {
            completeMultipart(asset, request);
        }

        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(asset.getS3Key())
                    .build());
        } catch (NoSuchKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Uploaded object not found in storage");
        } catch (software.amazon.awssdk.services.s3.model.S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new ApiException(
                        HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Uploaded object not found in storage");
            }
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemCodes.CONFLICT,
                    "Object storage error: " + ex.awsErrorDetails().errorMessage());
        } catch (software.amazon.awssdk.core.exception.SdkClientException ex) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, ProblemCodes.CONFLICT, "Object storage is unavailable");
        }

        asset.setStatus("processing");
        asset.setErrorMessage(null);
        asset.setUpdatedAt(Instant.now());
        mediaAssetRepository.save(asset);
        publishProcessCommand(asset.getId());

        auditRecorder.record(
                asset.getWorkspaceId(),
                userId,
                "media.upload.complete",
                "media_asset",
                asset.getId(),
                Map.of("status", "processing"),
                ctx == null ? null : ctx.ip());

        return toDto(asset, List.of());
    }

    @Override
    public AuthDtos.MessageResponse delete(UUID userId, UUID mediaId, ClientRequestContext ctx) {
        MediaAssetEntity asset = requireMedia(mediaId);
        access.requirePermission(asset.getWorkspaceId(), userId, "media.manage");

        if (isUsedInBlockingPosts(asset.getId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Media is used in scheduled or publishing posts");
        }

        asset.setDeletedAt(Instant.now());
        asset.setUpdatedAt(Instant.now());
        mediaAssetRepository.save(asset);

        auditRecorder.record(
                asset.getWorkspaceId(),
                userId,
                "media.delete",
                "media_asset",
                asset.getId(),
                Map.of(),
                ctx == null ? null : ctx.ip());
        return new AuthDtos.MessageResponse("Media deleted");
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MediaDtos.ImportUrlResponse importUrl(
            UUID userId, MediaDtos.ImportUrlRequest request, String idempotencyKey, ClientRequestContext ctx) {
        return idempotencyService.execute(
                userId,
                idempotencyKey,
                IDEMPOTENCY_SCOPE_IMPORT,
                request,
                MediaDtos.ImportUrlResponse.class,
                () -> writeTx.execute(status -> importUrlOnce(userId, request, ctx)));
    }

    private MediaDtos.ImportUrlResponse importUrlOnce(
            UUID userId, MediaDtos.ImportUrlRequest request, ClientRequestContext ctx) {
        access.requirePermission(request.workspaceId(), userId, "media.manage");
        try {
            UrlSafety.validateHttpUrl(request.url());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, ex.getMessage());
        }

        DownloadedBytes downloaded = downloadSafely(request.url());
        ensureStorageQuota(request.workspaceId(), downloaded.bytes().length);

        String filename = sanitizeFilename(
                request.filename() != null ? request.filename() : guessFilename(request.url()),
                ApiEnums.MediaKind.image,
                downloaded.contentType());
        ApiEnums.MediaKind kind = kindFromMime(downloaded.contentType());

        MediaAssetEntity asset = new MediaAssetEntity();
        asset.setWorkspaceId(request.workspaceId());
        asset.setUploaderId(userId);
        asset.setKind(kind.name());
        asset.setMime(downloaded.contentType());
        asset.setSizeBytes((long) downloaded.bytes().length);
        asset.setFilename(filename);
        asset.setTags(List.of());
        asset.setStatus("processing");
        asset.setOriginalUrl(request.url());
        asset.setDerivatives(Map.of());
        asset.setMeta(Map.of());
        asset.setCreatedAt(Instant.now());
        asset.setUpdatedAt(Instant.now());
        asset.setS3Key("pending");
        asset = mediaAssetRepository.save(asset);

        String s3Key = "workspaces/" + request.workspaceId() + "/media/" + asset.getId() + "/" + filename;
        asset.setS3Key(s3Key);
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(s3Properties.getBucket())
                            .key(s3Key)
                            .contentType(downloaded.contentType())
                            .build(),
                    RequestBody.fromBytes(downloaded.bytes()));
        } catch (software.amazon.awssdk.core.exception.SdkClientException
                | software.amazon.awssdk.services.s3.model.S3Exception ex) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, ProblemCodes.CONFLICT, "Object storage is unavailable");
        }
        mediaAssetRepository.save(asset);
        publishProcessCommand(asset.getId());

        auditRecorder.record(
                request.workspaceId(),
                userId,
                "media.import_url",
                "media_asset",
                asset.getId(),
                Map.of("url", request.url()),
                ctx == null ? null : ctx.ip());

        return new MediaDtos.ImportUrlResponse(asset.getId(), ApiEnums.MediaStatus.processing);
    }

    private void completeMultipart(MediaAssetEntity asset, MediaDtos.CompleteUploadRequest request) {
        List<String> etags = request.etags() == null ? List.of() : request.etags();
        if (etags.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "etags required for multipart complete");
        }
        List<CompletedPart> parts = new ArrayList<>();
        for (int i = 0; i < etags.size(); i++) {
            parts.add(
                    CompletedPart.builder().partNumber(i + 1).eTag(etags.get(i)).build());
        }
        s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(asset.getS3Key())
                .uploadId(request.uploadId())
                .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                .build());
    }

    private void publishProcessCommand(UUID mediaId) {
        RabbitTemplate template = rabbitTemplate.getIfAvailable();
        if (template == null) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, ProblemCodes.NOT_IMPLEMENTED, "Messaging is not configured");
        }
        template.convertAndSend(
                MediaTopology.MEDIA_EXCHANGE,
                MediaTopology.ROUTING_KEY_PROCESS,
                new MediaProcessCommand(mediaId, null));
    }

    private void ensureStorageQuota(UUID workspaceId, long additionalBytes) {
        WorkspaceEntity workspace = access.requireWorkspace(workspaceId);
        long used = mediaAssetRepository.sumSizeBytesByWorkspaceId(workspaceId);
        long limit = resolveStorageLimit(workspace);
        if (used + additionalBytes > limit) {
            throw new ApiException(
                    HttpStatus.PAYMENT_REQUIRED,
                    ProblemCodes.PLAN_LIMIT_EXCEEDED,
                    "Workspace storage limit exceeded",
                    Map.of("limit", "storage_bytes", "storage_bytes", limit, "used_bytes", used));
        }
    }

    private long resolveStorageLimit(WorkspaceEntity workspace) {
        if (workspace.getPlanId() == null) {
            return 1073741824L;
        }
        PlanEntity plan = planRepository.findById(workspace.getPlanId()).orElse(null);
        if (plan == null || plan.getLimits() == null) {
            return 1073741824L;
        }
        Object value = plan.getLimits().get("storage_bytes");
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 1073741824L;
    }

    private MediaAssetEntity requireMedia(UUID mediaId) {
        return mediaAssetRepository
                .findByIdAndDeletedAtIsNull(mediaId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Resource not found"));
    }

    private boolean isUsedInBlockingPosts(UUID mediaId) {
        Integer count = jdbcClient
                .sql(
                        """
                        SELECT COUNT(*)::int FROM (
                          SELECT p.id
                          FROM posts p
                          WHERE p.deleted_at IS NULL
                            AND p.status IN ('scheduled', 'publishing', 'pending_approval', 'approved')
                            AND (
                              p.base_content::text LIKE :needle
                              OR EXISTS (
                                SELECT 1 FROM post_variants v
                                WHERE v.post_id = p.id AND v.content::text LIKE :needle
                              )
                            )
                        ) t
                        """)
                .param("needle", "%" + mediaId + "%")
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    private List<MediaDtos.MediaUsage> findUsages(UUID mediaId) {
        return jdbcClient
                .sql(
                        """
                        SELECT p.id, p.title, p.status
                        FROM posts p
                        WHERE p.deleted_at IS NULL
                          AND (
                            p.base_content::text LIKE :needle
                            OR EXISTS (
                              SELECT 1 FROM post_variants v
                              WHERE v.post_id = p.id AND v.content::text LIKE :needle
                            )
                          )
                        ORDER BY p.id DESC
                        LIMIT 50
                        """)
                .param("needle", "%" + mediaId + "%")
                .query((rs, rowNum) -> new MediaDtos.MediaUsage(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("title"),
                        ApiEnums.PostStatus.valueOf(rs.getString("status"))))
                .list();
    }

    private DownloadedBytes downloadSafely(String startUrl) {
        String current = startUrl;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            URI uri = URI.create(current);
            try {
                UrlSafety.validateHttpUrl(current);
                // Re-resolve DNS after every hop
                UrlSafety.assertHostSafe(uri.getHost());
                for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                    if (UrlSafety.isBlockedAddress(address)) {
                        throw new ApiException(
                                HttpStatus.BAD_REQUEST,
                                ProblemCodes.VALIDATION_FAILED,
                                "Private or link-local addresses are not allowed");
                    }
                }
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, ex.getMessage());
            } catch (java.net.UnknownHostException ex) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Unable to resolve host");
            }

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .header("User-Agent", "posts-media-import/1.0")
                    .build();
            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (IOException | InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Failed to download URL");
            }

            int code = response.statusCode();
            if (code >= 300 && code < 400) {
                Optional<String> location = response.headers().firstValue("location");
                if (location.isEmpty()) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Redirect without Location");
                }
                current = uri.resolve(location.get()).toString();
                continue;
            }
            if (code < 200 || code >= 300) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Upstream returned " + code);
            }

            String rawContentType =
                    response.headers().firstValue("content-type").orElse("application/octet-stream");
            int semi = rawContentType.indexOf(';');
            String contentType = (semi >= 0 ? rawContentType.substring(0, semi) : rawContentType)
                    .trim()
                    .toLowerCase(Locale.ROOT);
            try (InputStream in = response.body()) {
                byte[] bytes = in.readNBytes((int) Math.min(MAX_IMPORT_BYTES + 1, Integer.MAX_VALUE));
                if (bytes.length > MAX_IMPORT_BYTES) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Remote file too large");
                }
                return new DownloadedBytes(bytes, contentType);
            } catch (IOException ex) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Failed to read remote body");
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Too many redirects");
    }

    private static MediaDtos.MediaAsset toDto(MediaAssetEntity entity, List<MediaDtos.MediaUsage> usedIn) {
        return new MediaDtos.MediaAsset(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getUploaderId(),
                ApiEnums.MediaKind.valueOf(entity.getKind()),
                entity.getMime(),
                entity.getSizeBytes() == null ? 0L : entity.getSizeBytes(),
                entity.getWidth(),
                entity.getHeight(),
                entity.getDurationMs(),
                entity.getSha256(),
                ApiEnums.MediaStatus.valueOf(entity.getStatus()),
                entity.getDerivatives() == null ? Map.of() : entity.getDerivatives(),
                entity.getCreatedAt(),
                entity.getFilename(),
                entity.getFolder(),
                entity.getTags() == null ? List.of() : List.copyOf(entity.getTags()),
                entity.getErrorMessage(),
                usedIn);
    }

    private static String sanitizeFilename(String filename, ApiEnums.MediaKind kind, String mime) {
        String base = filename == null || filename.isBlank() ? defaultFilename(kind, mime) : filename.trim();
        base = base.replace('\\', '_').replace('/', '_');
        if (base.length() > 180) {
            base = base.substring(base.length() - 180);
        }
        return base.isBlank() ? "file.bin" : base;
    }

    private static String defaultFilename(ApiEnums.MediaKind kind, String mime) {
        String ext =
                switch (kind) {
                    case image -> mime != null && mime.contains("png") ? "png" : "jpg";
                    case video, clip -> "mp4";
                    case audio -> "mp3";
                    case doc -> "bin";
                };
        return "upload." + ext;
    }

    private static String guessFilename(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path == null || path.isBlank() || path.endsWith("/")) {
                return null;
            }
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1) : path;
        } catch (Exception ex) {
            return null;
        }
    }

    private static ApiEnums.MediaKind kindFromMime(String mime) {
        if (mime == null) {
            return ApiEnums.MediaKind.doc;
        }
        if (mime.startsWith("image/")) {
            return ApiEnums.MediaKind.image;
        }
        if (mime.startsWith("video/")) {
            return ApiEnums.MediaKind.video;
        }
        if (mime.startsWith("audio/")) {
            return ApiEnums.MediaKind.audio;
        }
        return ApiEnums.MediaKind.doc;
    }

    /** Drop abandoned multipart sessions so storage quota and idempotency stay usable. */
    private void purgeStaleUploadingAssets(UUID workspaceId) {
        Instant cutoff = Instant.now().minus(s3Properties.getPresignTtl().multipliedBy(2));
        jdbcClient
                .sql(
                        """
                        DELETE FROM media_assets
                        WHERE workspace_id = :ws
                          AND status = 'uploading'
                          AND created_at < :cutoff
                        """)
                .param("ws", workspaceId)
                .param("cutoff", cutoff)
                .update();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final class DownloadedBytes {
        private final byte[] bytes;
        private final String contentType;

        private DownloadedBytes(byte[] bytes, String contentType) {
            this.bytes = bytes;
            this.contentType = contentType;
        }

        byte[] bytes() {
            return bytes;
        }

        String contentType() {
            return contentType;
        }
    }
}
