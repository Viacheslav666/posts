package ru.posts.api.media;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import ru.posts.common.s3.S3PresignUrls;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

/**
 * Real S3 multipart sessions. Each part gets its own presigned URL. Callers must PUT the part
 * bytes to that URL unchanged: appending {@code partNumber} or {@code uploadId} breaks the
 * signature.
 *
 * <p>Browsers can read the part {@code ETag} only when the object-store response exposes it.
 * Same-origin {@code /s3/} proxies should send {@code Access-Control-Expose-Headers: ETag} (Safari
 * hides {@code ETag} on cross-origin responses otherwise). See {@code full-gateway.conf}.
 */
final class S3MultipartUrls {

    /** S3 minimum size for every part except the last. Also the single-PUT threshold. */
    static final long PART_SIZE_BYTES = 5L * 1024 * 1024;

    static final int MAX_PARTS = 10_000;

    private S3MultipartUrls() {}

    static boolean usesMultipart(long sizeBytes) {
        return sizeBytes >= PART_SIZE_BYTES;
    }

    static int partCount(long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
        long count = (sizeBytes + PART_SIZE_BYTES - 1) / PART_SIZE_BYTES;
        if (count > MAX_PARTS) {
            throw new IllegalArgumentException("File exceeds multipart part limit");
        }
        return (int) count;
    }

    record PartUrl(int partNumber, String url) {}

    record Session(String uploadId, List<PartUrl> parts) {}

    static Session begin(
            S3Client s3Client,
            S3Presigner presigner,
            String bucket,
            String key,
            String contentType,
            long sizeBytes,
            Duration ttl,
            String publicBaseUrl) {
        int count = partCount(sizeBytes);
        String uploadId = s3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build())
                .uploadId();
        List<PartUrl> parts = new ArrayList<>(count);
        for (int partNumber = 1; partNumber <= count; partNumber++) {
            parts.add(new PartUrl(
                    partNumber, presignPart(presigner, bucket, key, uploadId, partNumber, ttl, publicBaseUrl)));
        }
        return new Session(uploadId, List.copyOf(parts));
    }

    static String presignPart(
            S3Presigner presigner,
            String bucket,
            String key,
            String uploadId,
            int partNumber,
            Duration ttl,
            String publicBaseUrl) {
        var presigned = presigner.presignUploadPart(UploadPartPresignRequest.builder()
                .signatureDuration(ttl)
                .uploadPartRequest(UploadPartRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .build())
                .build());
        return S3PresignUrls.toPublicUrl(presigned.url().toString(), publicBaseUrl);
    }

    /** S3 CompleteMultipartUpload expects the ETag quoted, matching the UploadPart header. */
    static String quoteEtag(String etag) {
        if (etag == null) {
            return null;
        }
        String value = etag.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value;
        }
        return "\"" + value + "\"";
    }
}
