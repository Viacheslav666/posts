package ru.posts.social.api;

import java.util.Arrays;
import java.util.Objects;

/**
 * Lightweight media asset descriptor for adapters (not a JPA entity). Bytes are resolved by the
 * caller or storage layer before upload when needed (BE-23).
 *
 * <p>Note: {@code contentBytes} is intentionally a defensive-copied array field (Error Prone
 * {@code ArrayRecordComponent} suppressed) — adapters need raw upload payloads.
 */
@SuppressWarnings("ArrayRecordComponent")
public record MediaAssetRef(
        String id, String kind, String mime, long sizeBytes, String sha256, String s3Key, byte[] contentBytes) {

    public MediaAssetRef {
        if (contentBytes != null) {
            contentBytes = Arrays.copyOf(contentBytes, contentBytes.length);
        }
    }

    public MediaAssetRef(String id, String kind, String mime, long sizeBytes, String sha256) {
        this(id, kind, mime, sizeBytes, sha256, null, null);
    }

    public MediaAssetRef withContentBytes(byte[] bytes) {
        return new MediaAssetRef(id, kind, mime, sizeBytes, sha256, s3Key, bytes);
    }

    public boolean hasContentBytes() {
        return contentBytes != null && contentBytes.length > 0;
    }

    @Override
    public byte[] contentBytes() {
        return contentBytes == null ? null : Arrays.copyOf(contentBytes, contentBytes.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MediaAssetRef that)) {
            return false;
        }
        return sizeBytes == that.sizeBytes
                && Objects.equals(id, that.id)
                && Objects.equals(kind, that.kind)
                && Objects.equals(mime, that.mime)
                && Objects.equals(sha256, that.sha256)
                && Objects.equals(s3Key, that.s3Key)
                && Arrays.equals(contentBytes, that.contentBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, kind, mime, sizeBytes, sha256, s3Key);
        result = 31 * result + Arrays.hashCode(contentBytes);
        return result;
    }
}
