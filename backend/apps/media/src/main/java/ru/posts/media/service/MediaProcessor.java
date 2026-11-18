package ru.posts.media.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.media.config.MediaProperties;
import ru.posts.media.config.S3Properties;
import ru.posts.persistence.entity.MediaAssetEntity;
import ru.posts.persistence.repository.MediaAssetRepository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class MediaProcessor {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessor.class);
    private static final Tika TIKA = new Tika();

    private final MediaAssetRepository mediaAssetRepository;
    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final MediaProperties mediaProperties;
    private final ObjectMapper objectMapper;

    public MediaProcessor(
            MediaAssetRepository mediaAssetRepository,
            S3Client s3Client,
            S3Properties s3Properties,
            MediaProperties mediaProperties,
            ObjectMapper objectMapper) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
        this.mediaProperties = mediaProperties;
        this.objectMapper = objectMapper;
    }

    /** Returns true to ack (success or terminal failure), false to nack without requeue (DLX). */
    @Transactional
    public boolean process(UUID mediaId) {
        MediaAssetEntity asset = mediaAssetRepository.findById(mediaId).orElse(null);
        if (asset == null || asset.getDeletedAt() != null) {
            log.warn("Media {} missing or deleted — ack", mediaId);
            return true;
        }

        if ("ready".equals(asset.getStatus())
                && asset.getDerivatives() != null
                && !asset.getDerivatives().isEmpty()) {
            log.info("Media {} already ready — idempotent ack", mediaId);
            return true;
        }

        if ("failed".equals(asset.getStatus()) && asset.getErrorMessage() != null) {
            log.info("Media {} already failed — ack", mediaId);
            return true;
        }

        if (!"processing".equals(asset.getStatus()) && !"uploading".equals(asset.getStatus())) {
            log.warn("Media {} unexpected status {} — ack", mediaId, asset.getStatus());
            return true;
        }

        if ("uploading".equals(asset.getStatus())) {
            asset.setStatus("processing");
            asset.setUpdatedAt(Instant.now());
            mediaAssetRepository.save(asset);
        }

        if (mediaProperties.getClamav().isEnabled()) {
            log.info("ClamAV enabled but scanner stubbed (P1) for media {}", mediaId);
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("posts-media-" + mediaId);
            Path original = tempDir.resolve("original");
            downloadOriginal(asset, original);

            long size = Files.size(original);
            long limit = mediaProperties.getLimits().forKind(asset.getKind());
            if (size > limit) {
                return markFailed(asset, "File exceeds size limit for kind " + asset.getKind());
            }

            String detected = TIKA.detect(original);
            if (!mimeMatchesKind(detected, asset.getKind(), asset.getMime())) {
                return markFailed(
                        asset,
                        "MIME mismatch: declared=" + asset.getMime() + " detected=" + detected + " kind="
                                + asset.getKind());
            }

            String sha256 = sha256Hex(original);
            Map<String, Object> derivatives = new HashMap<>();
            if (asset.getDerivatives() != null) {
                derivatives.putAll(asset.getDerivatives());
            }

            switch (asset.getKind()) {
                case "image" -> processImage(asset, original, tempDir, derivatives);
                case "video" -> processVideo(asset, original, tempDir, derivatives);
                case "audio", "doc" -> {
                    // metadata-only for MVP
                }
                default -> {
                    return markFailed(asset, "Unsupported kind " + asset.getKind());
                }
            }

            asset.setMime(detected);
            asset.setSizeBytes(size);
            asset.setSha256(sha256);
            asset.setDerivatives(derivatives);
            asset.setStatus("ready");
            asset.setErrorMessage(null);
            asset.setUpdatedAt(Instant.now());
            mediaAssetRepository.save(asset);
            log.info("Media {} ready", mediaId);
            return true;
        } catch (Exception ex) {
            log.error("Failed processing media {}", mediaId, ex);
            return markFailed(asset, ex.getMessage() == null ? "processing_failed" : ex.getMessage());
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    private void processImage(MediaAssetEntity asset, Path original, Path tempDir, Map<String, Object> derivatives)
            throws IOException {
        Path normalized = tempDir.resolve("normalized.jpg");
        Thumbnails.of(original.toFile()).scale(1.0).outputFormat("jpg").toFile(normalized.toFile());

        BufferedImage image = ImageIO.read(normalized.toFile());
        if (image != null) {
            asset.setWidth(image.getWidth());
            asset.setHeight(image.getHeight());
        }

        Path preview = tempDir.resolve("preview.jpg");
        int edge = mediaProperties.getPreviewMaxEdge();
        Thumbnails.of(normalized.toFile())
                .size(edge, edge)
                .keepAspectRatio(true)
                .outputFormat("jpg")
                .toFile(preview.toFile());

        String previewKey = derivativeKey(asset, "preview.jpg");
        uploadFile(previewKey, preview, "image/jpeg");
        derivatives.put("preview", Map.of("s3Key", previewKey, "mime", "image/jpeg"));

        // Replace original with EXIF-stripped bytes
        uploadFile(asset.getS3Key(), normalized, "image/jpeg");
        asset.setMime("image/jpeg");
    }

    private void processVideo(MediaAssetEntity asset, Path original, Path tempDir, Map<String, Object> derivatives)
            throws IOException, InterruptedException {
        FfprobeMeta meta = ffprobe(original);
        asset.setWidth(meta.width());
        asset.setHeight(meta.height());
        asset.setDurationMs(meta.durationMs());

        Path landscape = tempDir.resolve("transcode-16x9.mp4");
        runFfmpeg(List.of(
                "ffmpeg",
                "-y",
                "-i",
                original.toString(),
                "-c:v",
                "libx264",
                "-preset",
                "veryfast",
                "-crf",
                "23",
                "-vf",
                "scale='min(1280,iw)':-2",
                "-c:a",
                "aac",
                "-b:a",
                "128k",
                "-movflags",
                "+faststart",
                landscape.toString()));

        Path vertical = tempDir.resolve("transcode-9x16.mp4");
        runFfmpeg(List.of(
                "ffmpeg",
                "-y",
                "-i",
                original.toString(),
                "-c:v",
                "libx264",
                "-preset",
                "veryfast",
                "-crf",
                "23",
                "-vf",
                "scale=720:1280:force_original_aspect_ratio=decrease,pad=720:1280:(ow-iw)/2:(oh-ih)/2",
                "-c:a",
                "aac",
                "-b:a",
                "128k",
                "-movflags",
                "+faststart",
                vertical.toString()));

        Path thumb = tempDir.resolve("thumb.jpg");
        runFfmpeg(List.of(
                "ffmpeg",
                "-y",
                "-ss",
                "0",
                "-i",
                original.toString(),
                "-frames:v",
                "1",
                "-q:v",
                "2",
                thumb.toString()));

        String landscapeKey = derivativeKey(asset, "mp4-16x9.mp4");
        String verticalKey = derivativeKey(asset, "mp4-9x16.mp4");
        String thumbKey = derivativeKey(asset, "thumb.jpg");
        uploadFile(landscapeKey, landscape, "video/mp4");
        uploadFile(verticalKey, vertical, "video/mp4");
        uploadFile(thumbKey, thumb, "image/jpeg");

        derivatives.put("mp4_16x9", Map.of("s3Key", landscapeKey, "mime", "video/mp4"));
        derivatives.put("mp4_9x16", Map.of("s3Key", verticalKey, "mime", "video/mp4"));
        derivatives.put("thumbnail", Map.of("s3Key", thumbKey, "mime", "image/jpeg"));
    }

    private void downloadOriginal(MediaAssetEntity asset, Path target) throws IOException {
        try (InputStream in = s3Client.getObject(GetObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(asset.getS3Key())
                .build())) {
            Files.copy(in, target);
        }
    }

    private void uploadFile(String key, Path file, String contentType) throws IOException {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(s3Properties.getBucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromFile(file));
    }

    private boolean markFailed(MediaAssetEntity asset, String message) {
        asset.setStatus("failed");
        asset.setErrorMessage(message == null ? "failed" : message.substring(0, Math.min(message.length(), 2000)));
        asset.setUpdatedAt(Instant.now());
        mediaAssetRepository.save(asset);
        return true;
    }

    private static String derivativeKey(MediaAssetEntity asset, String name) {
        return asset.getS3Key() + "/derivatives/" + name;
    }

    static boolean mimeMatchesKind(String detected, String kind, String declared) {
        if (detected == null || detected.isBlank()) {
            return false;
        }
        String d = detected.toLowerCase(Locale.ROOT);
        return switch (kind) {
            case "image" -> d.startsWith("image/");
            case "video" -> d.startsWith("video/");
            case "audio" -> d.startsWith("audio/");
            case "doc" -> !d.startsWith("image/") && !d.startsWith("video/") && !d.startsWith("audio/");
            default -> false;
        };
    }

    private FfprobeMeta ffprobe(Path file) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                        "ffprobe",
                        "-v",
                        "quiet",
                        "-print_format",
                        "json",
                        "-show_format",
                        "-show_streams",
                        file.toString())
                .redirectErrorStream(true)
                .start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("ffprobe timed out");
        }
        if (process.exitValue() != 0) {
            throw new IOException("ffprobe failed: " + out);
        }
        JsonNode root = objectMapper.readTree(out);
        Integer width = null;
        Integer height = null;
        Integer durationMs = null;
        JsonNode streams = root.path("streams");
        if (streams.isArray()) {
            for (JsonNode stream : streams) {
                if ("video".equals(stream.path("codec_type").asText())) {
                    width = stream.path("width").isMissingNode()
                            ? null
                            : stream.path("width").asInt();
                    height = stream.path("height").isMissingNode()
                            ? null
                            : stream.path("height").asInt();
                    break;
                }
            }
        }
        String duration = root.path("format").path("duration").asText(null);
        if (duration != null) {
            try {
                durationMs = (int) Math.round(Double.parseDouble(duration) * 1000.0);
            } catch (NumberFormatException ignored) {
                // leave null
            }
        }
        return new FfprobeMeta(width, height, durationMs);
    }

    private static void runFfmpeg(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("ffmpeg timed out");
        }
        if (process.exitValue() != 0) {
            throw new IOException("ffmpeg failed: " + out);
        }
    }

    private static String sha256Hex(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void deleteRecursively(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private record FfprobeMeta(Integer width, Integer height, Integer durationMs) {}
}
