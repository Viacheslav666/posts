package ru.posts.publisher.media;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import ru.posts.persistence.jdbc.ChannelMediaCacheJdbc;
import ru.posts.persistence.jdbc.MediaAssetLoadJdbc;
import ru.posts.persistence.jdbc.PublicationClaim;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.MediaAssetRef;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.PostVariantMeta;
import ru.posts.social.api.PublishResult;
import ru.posts.social.api.RemoteMediaRef;
import ru.posts.social.api.SocialAdapter;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * Resolves {@code mediaIds} from the claim, downloads bytes from S3 when configured, reuses
 * {@code channel_media_cache}, and uploads missing assets via {@link SocialAdapter#uploadMedia}.
 *
 * <p>Networks with {@code features.inlineMediaPublish=true} (Telegram) keep pending bytes in {@code
 * meta.media} so the adapter can send them as part of {@code publish}; file_ids returned in {@link
 * PublishResult#uploadedMediaByAssetId()} are cached afterwards.
 */
@Service
public class MediaPublishSupport {

    private static final Logger log = LoggerFactory.getLogger(MediaPublishSupport.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final MediaAssetLoadJdbc mediaAssetLoadJdbc;
    private final ChannelMediaCacheJdbc channelMediaCacheJdbc;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<S3Client> s3Client;
    private final PublisherS3Properties s3Properties;

    public MediaPublishSupport(
            MediaAssetLoadJdbc mediaAssetLoadJdbc,
            ChannelMediaCacheJdbc channelMediaCacheJdbc,
            ObjectMapper objectMapper,
            ObjectProvider<S3Client> s3Client,
            PublisherS3Properties s3Properties) {
        this.mediaAssetLoadJdbc = mediaAssetLoadJdbc;
        this.channelMediaCacheJdbc = channelMediaCacheJdbc;
        this.objectMapper = objectMapper;
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    /** Options + fingerprint only (safe for findExisting / UNKNOWN_OUTCOME). */
    public PostVariantContent withOptionsMeta(PublicationClaim claim, PostVariantContent base) {
        Map<String, Object> meta = new LinkedHashMap<>(base.meta());
        Map<String, Object> options = parseOptions(claim.optionsJson());
        if (!options.isEmpty()) {
            meta.put(PostVariantMeta.OPTIONS, options);
        }
        String fingerprint = sha16(claim.idempotencyKey());
        if (fingerprint != null) {
            meta.put(PostVariantMeta.IDEMPOTENCY_FINGERPRINT, fingerprint);
        }
        return base.withMeta(meta);
    }

    public PostVariantContent enrich(
            PublicationClaim claim, PostVariantContent base, SocialAdapter adapter, ChannelContext ctx) {
        PostVariantContent withMeta = withOptionsMeta(claim, base);
        Map<String, Object> meta = new LinkedHashMap<>(withMeta.meta());

        List<UUID> mediaIds = parseMediaIds(claim.mediaIdsJson());
        List<MediaAssetRef> assets = loadAssets(claim.workspaceId(), mediaIds);
        List<String> attachments = new ArrayList<>();
        List<MediaAssetRef> pendingUpload = new ArrayList<>();

        for (MediaAssetRef asset : assets) {
            Optional<ChannelMediaCacheJdbc.CachedMedia> cached =
                    channelMediaCacheJdbc.find(claim.channelId(), UUID.fromString(asset.id()));
            if (cached.isPresent()) {
                attachments.add(cached.get().remoteRef());
            } else {
                pendingUpload.add(asset);
            }
        }

        boolean inlineMediaPublish =
                Boolean.TRUE.equals(adapter.capabilities().features().get("inlineMediaPublish"));

        if (inlineMediaPublish) {
            List<MediaAssetRef> pendingWithBytes = new ArrayList<>();
            for (MediaAssetRef asset : pendingUpload) {
                pendingWithBytes.add(ensureBytes(asset));
            }
            if (!pendingWithBytes.isEmpty()) {
                meta.put(PostVariantMeta.MEDIA, pendingWithBytes);
            }
        } else {
            for (MediaAssetRef asset : pendingUpload) {
                MediaAssetRef withBytes = ensureBytes(asset);
                RemoteMediaRef remote = adapter.uploadMedia(ctx, withBytes);
                if (remote != null && remote.attachmentId() != null) {
                    attachments.add(remote.attachmentId());
                    channelMediaCacheJdbc.upsert(
                            claim.channelId(),
                            UUID.fromString(asset.id()),
                            remote.attachmentId(),
                            remote.kind() == null ? "other" : normalizeCacheKind(remote.kind()),
                            Instant.now().plus(7, ChronoUnit.DAYS));
                }
            }
        }

        if (!attachments.isEmpty()) {
            meta.put(PostVariantMeta.ATTACHMENTS, attachments);
        }
        return withMeta.withMeta(meta);
    }

    /** Persist file_ids / remote refs returned by inline-media publish (Telegram albums). */
    public void cacheUploadedFromPublish(UUID channelId, PublishResult result) {
        if (result == null
                || result.uploadedMediaByAssetId() == null
                || result.uploadedMediaByAssetId().isEmpty()) {
            return;
        }
        Instant expires = Instant.now().plus(7, ChronoUnit.DAYS);
        for (Map.Entry<String, RemoteMediaRef> entry :
                result.uploadedMediaByAssetId().entrySet()) {
            if (entry.getKey() == null
                    || entry.getValue() == null
                    || entry.getValue().attachmentId() == null) {
                continue;
            }
            try {
                UUID assetId = UUID.fromString(entry.getKey());
                RemoteMediaRef remote = entry.getValue();
                channelMediaCacheJdbc.upsert(
                        channelId,
                        assetId,
                        remote.attachmentId(),
                        remote.kind() == null ? "other" : normalizeCacheKind(remote.kind()),
                        expires);
            } catch (IllegalArgumentException ex) {
                log.warn("Skipping cache for non-UUID media key {}: {}", entry.getKey(), ex.getMessage());
            }
        }
    }

    private List<MediaAssetRef> loadAssets(UUID workspaceId, List<UUID> mediaIds) {
        if (mediaIds.isEmpty()) {
            return List.of();
        }
        List<MediaAssetLoadJdbc.MediaAssetRow> rows = mediaAssetLoadJdbc.findReady(workspaceId, mediaIds);
        List<MediaAssetRef> out = new ArrayList<>();
        for (MediaAssetLoadJdbc.MediaAssetRow row : rows) {
            if (!"ready".equalsIgnoreCase(row.status()) && !"processing".equalsIgnoreCase(row.status())) {
                log.warn("Skipping media {} with status {}", row.id(), row.status());
                continue;
            }
            out.add(new MediaAssetRef(
                    row.id().toString(), row.kind(), row.mime(), row.sizeBytes(), row.sha256(), row.s3Key(), null));
        }
        return out;
    }

    private MediaAssetRef ensureBytes(MediaAssetRef asset) {
        if (asset.hasContentBytes()) {
            return asset;
        }
        S3Client client = s3Client.getIfAvailable();
        if (client == null
                || !s3Properties.isEnabled()
                || asset.s3Key() == null
                || asset.s3Key().isBlank()) {
            // Tests / emulator without S3: tiny stub matching kind.
            byte[] stub = stubBytes(asset);
            return asset.withContentBytes(stub);
        }
        ResponseBytes<GetObjectResponse> bytes = client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(asset.s3Key())
                .build());
        return asset.withContentBytes(bytes.asByteArray());
    }

    private static byte[] stubBytes(MediaAssetRef asset) {
        String kind = asset.kind() == null ? "" : asset.kind().toLowerCase(Locale.ROOT);
        String mime = asset.mime() == null ? "" : asset.mime().toLowerCase(Locale.ROOT);
        if (kind.contains("video") || mime.startsWith("video/")) {
            return new byte[] {0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70};
        }
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
    }

    private Map<String, Object> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank() || "{}".equals(optionsJson.trim())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(optionsJson, MAP);
        } catch (Exception ex) {
            log.warn("Failed to parse options JSON: {}", ex.getMessage());
            return Map.of();
        }
    }

    private List<UUID> parseMediaIds(String mediaIdsJson) {
        if (mediaIdsJson == null || mediaIdsJson.isBlank() || "[]".equals(mediaIdsJson.trim())) {
            return List.of();
        }
        try {
            List<String> raw = objectMapper.readValue(mediaIdsJson, STRING_LIST);
            List<UUID> out = new ArrayList<>();
            for (String id : raw) {
                if (id != null && !id.isBlank()) {
                    out.add(UUID.fromString(id));
                }
            }
            return out;
        } catch (Exception ex) {
            log.warn("Failed to parse mediaIds JSON: {}", ex.getMessage());
            return List.of();
        }
    }

    private static String normalizeCacheKind(String kind) {
        String k = kind == null ? "other" : kind.toLowerCase(Locale.ROOT);
        if (k.contains("photo") || k.contains("image")) {
            return "photo";
        }
        if (k.contains("clip")) {
            return "clip";
        }
        if (k.contains("video") || k.contains("movie")) {
            return "video";
        }
        if (k.contains("doc") || k.contains("file")) {
            return "doc";
        }
        if (k.contains("audio")) {
            return "audio";
        }
        return "other";
    }

    private static String sha16(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 16);
        } catch (Exception ex) {
            return value;
        }
    }
}
