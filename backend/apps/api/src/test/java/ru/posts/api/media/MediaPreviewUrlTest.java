package ru.posts.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import ru.posts.api.config.S3Properties;
import ru.posts.api.idempotency.IdempotencyService;
import ru.posts.api.web.dto.media.MediaDtos;
import ru.posts.api.workspace.AuditRecorder;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.common.s3.S3ClientFactory;
import ru.posts.persistence.entity.MediaAssetEntity;
import ru.posts.persistence.repository.MediaAssetRepository;
import ru.posts.persistence.repository.PlanRepository;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

class MediaPreviewUrlTest {

    private static final String PUBLIC_BASE = "http://100.102.9.56:3000/s3";

    private S3Presigner presigner;
    private MediaServiceImpl service;

    @BeforeEach
    void setUp() {
        S3Properties properties = new S3Properties();
        properties.setEndpoint("http://minio:9000");
        properties.setBucket("posts-media");
        properties.setPresignPublicBaseUrl(PUBLIC_BASE);
        presigner = S3ClientFactory.createPresigner(properties.toSettings());
        service = newService(presigner, properties);
    }

    @AfterEach
    void tearDown() {
        if (presigner != null) {
            presigner.close();
        }
    }

    @Test
    void previewDerivativeWinsOverThumbnailAndOriginal() {
        Map<String, Object> derivatives = Map.of(
                "preview", Map.of("s3Key", "deriv/preview.jpg", "mime", "image/jpeg"),
                "thumbnail", Map.of("s3Key", "deriv/thumb.jpg", "mime", "image/jpeg"));

        assertThat(MediaServiceImpl.selectPreviewKey("video", "original.mp4", derivatives))
                .isEqualTo("deriv/preview.jpg");
        assertThat(MediaServiceImpl.selectPreviewKey("image", "original.jpg", derivatives))
                .isEqualTo("deriv/preview.jpg");
    }

    @Test
    void videoUsesThumbnailWhenPreviewIsMissing() {
        Map<String, Object> derivatives = Map.of(
                "thumbnail", Map.of("s3Key", "deriv/thumb.jpg", "mime", "image/jpeg"),
                "mp4_16x9", Map.of("s3Key", "deriv/wide.mp4", "mime", "video/mp4"));

        assertThat(MediaServiceImpl.selectPreviewKey("video", "original.mp4", derivatives))
                .isEqualTo("deriv/thumb.jpg");
        assertThat(MediaServiceImpl.selectPreviewKey("clip", "original.mp4", derivatives))
                .isEqualTo("deriv/thumb.jpg");
    }

    @Test
    void imageFallsBackToOriginalObject() {
        assertThat(MediaServiceImpl.selectPreviewKey("image", "workspaces/a/photo.jpg", Map.of()))
                .isEqualTo("workspaces/a/photo.jpg");
        assertThat(MediaServiceImpl.selectPreviewKey("image", "workspaces/a/photo.jpg", null))
                .isEqualTo("workspaces/a/photo.jpg");
        assertThat(MediaServiceImpl.selectPreviewKey("image", "  ", Map.of())).isNull();
    }

    @Test
    void videoWithoutPosterHasNoPreviewKey() {
        Map<String, Object> derivatives = Map.of("mp4_16x9", Map.of("s3Key", "deriv/wide.mp4", "mime", "video/mp4"));

        assertThat(MediaServiceImpl.selectPreviewKey("video", "original.mp4", derivatives))
                .isNull();
        assertThat(MediaServiceImpl.selectPreviewKey("clip", "original.mp4", Map.of()))
                .isNull();
        assertThat(MediaServiceImpl.selectPreviewKey("doc", "file.pdf", Map.of()))
                .isNull();
        assertThat(MediaServiceImpl.selectPreviewKey("audio", "file.mp3", null)).isNull();
    }

    @Test
    void toDtoPresignsSelectedKeyAsPublicGet() {
        MediaAssetEntity entity = asset(
                "image",
                "workspaces/a/photo.jpg",
                Map.of("preview", Map.of("s3Key", "workspaces/a/photo.jpg/derivatives/preview.jpg")));

        MediaDtos.MediaAsset dto = service.toDto(entity, List.of());

        assertThat(dto.previewUrl())
                .startsWith(PUBLIC_BASE + "/")
                .contains("posts-media/workspaces/a/photo.jpg/derivatives/preview.jpg")
                .contains("X-Amz-Expires=3600")
                .doesNotContain("minio");
    }

    @Test
    void presignFailureReturnsNullInsteadOfFailingTheAsset() {
        S3Presigner failing = mock(S3Presigner.class);
        when(failing.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(new IllegalStateException("sign failed"));
        S3Properties properties = new S3Properties();
        properties.setPresignPublicBaseUrl(PUBLIC_BASE);
        MediaServiceImpl failingService = newService(failing, properties);
        MediaAssetEntity entity = asset(
                "video", "original.mp4", Map.of("thumbnail", Map.of("s3Key", "deriv/thumb.jpg", "mime", "image/jpeg")));

        assertThatCode(() -> failingService.toDto(entity, List.of())).doesNotThrowAnyException();
        assertThat(failingService.toDto(entity, List.of()).previewUrl()).isNull();
    }

    private static MediaServiceImpl newService(S3Presigner s3Presigner, S3Properties properties) {
        return new MediaServiceImpl(
                mock(MediaAssetRepository.class),
                mock(PlanRepository.class),
                mock(WorkspaceAccessService.class),
                mock(AuditRecorder.class),
                mock(S3Client.class),
                s3Presigner,
                properties,
                mock(ObjectProvider.class),
                mock(IdempotencyService.class),
                mock(PlatformTransactionManager.class),
                mock(DataSource.class));
    }

    private static MediaAssetEntity asset(String kind, String key, Map<String, Object> derivatives) {
        MediaAssetEntity entity = new MediaAssetEntity();
        entity.setId(UUID.fromString("019a0000-0004-7000-8000-000000000004"));
        entity.setWorkspaceId(UUID.fromString("019a0000-0002-7000-8000-000000000002"));
        entity.setUploaderId(UUID.fromString("019a0000-0001-7000-8000-000000000001"));
        entity.setKind(kind);
        entity.setS3Key(key);
        entity.setMime("image".equals(kind) ? "image/jpeg" : "video/mp4");
        entity.setSizeBytes(10L);
        entity.setStatus("ready");
        entity.setDerivatives(derivatives);
        entity.setCreatedAt(Instant.parse("2026-11-01T00:00:00Z"));
        entity.setFilename("file.bin");
        return entity;
    }
}
