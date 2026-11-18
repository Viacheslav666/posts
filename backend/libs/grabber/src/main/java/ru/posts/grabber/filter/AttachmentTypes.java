package ru.posts.grabber.filter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Normalizes attachment type aliases used by grabber filters/transforms. */
public final class AttachmentTypes {

    private AttachmentTypes() {}

    public static Set<String> presentTypes(List<Map<String, Object>> attachments) {
        Set<String> out = new LinkedHashSet<>();
        if (attachments == null) {
            return out;
        }
        for (Map<String, Object> att : attachments) {
            if (att == null) {
                continue;
            }
            out.addAll(aliasesFor(att));
        }
        return out;
    }

    public static boolean hasMedia(List<Map<String, Object>> attachments) {
        Set<String> types = presentTypes(attachments);
        return types.contains("photo")
                || types.contains("video")
                || types.contains("clip")
                || types.contains("audio")
                || types.contains("doc")
                || types.contains("album");
    }

    public static boolean containsAny(Set<String> present, Collection<String> wanted) {
        if (wanted == null || wanted.isEmpty() || present == null) {
            return false;
        }
        for (String raw : wanted) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String key = canonicalize(raw);
            if ("none".equals(key)) {
                if (present.isEmpty()) {
                    return true;
                }
                continue;
            }
            if (present.contains(key)) {
                return true;
            }
            if ("audio".equals(key) && present.contains("music")) {
                return true;
            }
            if ("music".equals(key) && present.contains("audio")) {
                return true;
            }
            if (("file".equals(key) && present.contains("doc")) || ("doc".equals(key) && present.contains("file"))) {
                return true;
            }
            if (("product".equals(key) && present.contains("market"))
                    || ("market".equals(key) && present.contains("product"))) {
                return true;
            }
            if (("article".equals(key) && present.contains("wiki"))
                    || ("wiki".equals(key) && (present.contains("article") || present.contains("page")))) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsAll(Set<String> present, Collection<String> wanted) {
        if (wanted == null || wanted.isEmpty()) {
            return true;
        }
        for (String raw : wanted) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            if (!containsAny(present, List.of(raw))) {
                return false;
            }
        }
        return true;
    }

    public static String canonicalize(String raw) {
        if (raw == null) {
            return "none";
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "music" -> "audio";
            case "file", "document", "documents" -> "doc";
            case "product", "goods" -> "market";
            case "clips", "short_video", "shortvideo" -> "clip";
            case "articles", "page" -> "article";
            case "photos" -> "photo";
            case "videos" -> "video";
            case "polls" -> "poll";
            case "links" -> "link";
            case "playlists", "playlist" -> "playlist";
            case "albums" -> "album";
            case "market_albums", "market-album", "selection" -> "market_album";
            default -> v;
        };
    }

    private static Set<String> aliasesFor(Map<String, Object> att) {
        Set<String> out = new LinkedHashSet<>();
        String type = String.valueOf(att.getOrDefault("type", "none")).toLowerCase(Locale.ROOT);
        out.add(canonicalize(type));
        Object videoType = att.get("video_type");
        if (videoType != null) {
            String vt = videoType.toString().toLowerCase(Locale.ROOT);
            if (vt.contains("short") || "clip".equals(vt)) {
                out.add("clip");
            }
        }
        if (Boolean.TRUE.equals(att.get("is_clip")) || "true".equalsIgnoreCase(String.valueOf(att.get("is_clip")))) {
            out.add("clip");
        }
        if ("page".equals(type)) {
            out.add("article");
            out.add("wiki");
        }
        if ("audio".equals(type)) {
            out.add("music");
            out.add("audio");
        }
        if ("doc".equals(type)) {
            out.add("file");
        }
        if ("market".equals(type)) {
            out.add("product");
        }
        return out;
    }
}
