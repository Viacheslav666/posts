package ru.posts.social.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keys and helpers for {@link PostVariantContent#meta()} used by the publisher → adapter contract
 * (BE-23). Options JSON from {@code post_variants.options} is nested under {@link #OPTIONS}.
 */
public final class PostVariantMeta {

    public static final String OPTIONS = "options";
    public static final String MEDIA = "media";
    public static final String ATTACHMENTS = "attachments";
    public static final String IDEMPOTENCY_FINGERPRINT = "idempotencyFingerprint";

    private PostVariantMeta() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> options(Map<String, Object> meta) {
        if (meta == null) {
            return Map.of();
        }
        Object raw = meta.get(OPTIONS);
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return out;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<MediaAssetRef> media(Map<String, Object> meta) {
        if (meta == null) {
            return List.of();
        }
        Object raw = meta.get(MEDIA);
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<MediaAssetRef> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof MediaAssetRef ref) {
                out.add(ref);
            } else if (item instanceof Map<?, ?> map) {
                out.add(fromMap(map));
            }
        }
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    public static List<String> attachments(Map<String, Object> meta) {
        if (meta == null) {
            return List.of();
        }
        Object raw = meta.get(ATTACHMENTS);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                String value = String.valueOf(item);
                if (!value.isBlank()) {
                    out.add(value);
                }
            }
        }
        return List.copyOf(out);
    }

    public static String fingerprint(Map<String, Object> meta) {
        if (meta == null) {
            return null;
        }
        Object raw = meta.get(IDEMPOTENCY_FINGERPRINT);
        return raw == null ? null : String.valueOf(raw);
    }

    public static Map<String, Object> withAttachments(Map<String, Object> meta, List<String> attachments) {
        Map<String, Object> out = meta == null ? new LinkedHashMap<>() : new LinkedHashMap<>(meta);
        out.put(ATTACHMENTS, List.copyOf(attachments));
        return Map.copyOf(out);
    }

    private static MediaAssetRef fromMap(Map<?, ?> map) {
        String id = stringVal(map, "id");
        String kind = stringVal(map, "kind");
        String mime = stringVal(map, "mime");
        long size = longVal(map, "sizeBytes");
        String sha = stringVal(map, "sha256");
        String s3 = stringVal(map, "s3Key");
        byte[] bytes = null;
        Object rawBytes = map.get("contentBytes");
        if (rawBytes instanceof byte[] b) {
            bytes = b;
        }
        return new MediaAssetRef(id, kind, mime, size, sha, s3, bytes);
    }

    private static String stringVal(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static long longVal(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
