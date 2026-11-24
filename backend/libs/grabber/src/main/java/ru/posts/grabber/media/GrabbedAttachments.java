package ru.posts.grabber.media;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Precise attachment kinds for grabbed VK posts and conversion of stored attachment maps (see
 * {@code VkWallFetcher}) into VK {@code wall.post} attachment strings.
 */
public final class GrabbedAttachments {

    public static final String PHOTO = "photo";
    public static final String VIDEO = "video";
    public static final String CLIP = "clip";
    public static final String AUDIO = "audio";
    public static final String AUDIO_PLAYLIST = "audio_playlist";
    public static final String DOC = "doc";
    public static final String POLL = "poll";
    public static final String ALBUM = "album";
    public static final String MARKET = "market";
    public static final String MARKET_ALBUM = "market_album";
    public static final String WIKI = "wiki";
    public static final String LINK = "link";
    public static final String ARTICLE = "article";
    public static final String OTHER = "other";

    /** Kinds shown in the post media block (VK carousel applies only to these). */
    public static final Set<String> VISUAL_KINDS = Set.of(PHOTO, VIDEO, CLIP);

    /** VK wall.post accepts at most 10 attachments. */
    public static final int VK_MAX_ATTACHMENTS = 10;

    private GrabbedAttachments() {}

    public static String kindOf(Map<String, Object> att) {
        if (att == null) {
            return OTHER;
        }
        Object explicit = att.get("kind");
        if (explicit != null && !explicit.toString().isBlank()) {
            return explicit.toString().toLowerCase(Locale.ROOT);
        }
        String type = String.valueOf(att.getOrDefault("type", "")).toLowerCase(Locale.ROOT);
        return switch (type) {
            case "photo", "posted_photo", "graffiti" -> PHOTO;
            case "video" -> isClip(att) ? CLIP : VIDEO;
            case "clip", "short_video" -> CLIP;
            case "audio", "music" -> AUDIO;
            case "audio_playlist", "playlist" -> AUDIO_PLAYLIST;
            case "doc", "file" -> DOC;
            case "poll" -> POLL;
            case "album" -> ALBUM;
            case "market", "product" -> MARKET;
            case "market_album" -> MARKET_ALBUM;
            case "page", "wiki" -> WIKI;
            case "link" -> LINK;
            case "article" -> ARTICLE;
            default -> OTHER;
        };
    }

    /** Maps user-facing aliases (file, music, product, page, …) onto a precise kind. */
    public static String kindAlias(String raw) {
        if (raw == null) {
            return OTHER;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "photo", "photos" -> PHOTO;
            case "video", "videos" -> VIDEO;
            case "clip", "clips", "short_video" -> CLIP;
            case "audio", "music" -> AUDIO;
            case "audio_playlist", "playlist", "playlists" -> AUDIO_PLAYLIST;
            case "doc", "docs", "file", "files", "document", "documents" -> DOC;
            case "poll", "polls" -> POLL;
            case "album", "albums" -> ALBUM;
            case "market", "product", "products", "goods" -> MARKET;
            case "market_album", "market_albums", "selection" -> MARKET_ALBUM;
            case "wiki", "page", "pages" -> WIKI;
            case "link", "links" -> LINK;
            case "article", "articles" -> ARTICLE;
            default -> v;
        };
    }

    public static boolean isClip(Map<String, Object> att) {
        Object flag = att.get("is_clip");
        if (Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag))) {
            return true;
        }
        Object videoType = att.get("video_type");
        return videoType != null
                && videoType.toString().toLowerCase(Locale.ROOT).contains("short");
    }

    public static long countKinds(Collection<Map<String, Object>> atts, Set<String> kinds) {
        if (atts == null) {
            return 0;
        }
        return atts.stream().filter(a -> kinds.contains(kindOf(a))).count();
    }

    /**
     * VK attachment string referencing the original object ({@code photo-1_2_key}, {@code
     * video-1_2}, …) or the URL for links/articles. {@code null} when the map lacks the fields.
     */
    public static String reference(Map<String, Object> att) {
        if (att == null) {
            return null;
        }
        String kind = kindOf(att);
        if (LINK.equals(kind) || ARTICLE.equals(kind)) {
            String url = str(att.get("url"));
            return url == null || !url.startsWith("http") ? null : url;
        }
        String prefix =
                switch (kind) {
                    case PHOTO -> "photo";
                    case VIDEO, CLIP -> "video";
                    case AUDIO -> "audio";
                    case AUDIO_PLAYLIST -> "audio_playlist";
                    case DOC -> "doc";
                    case POLL -> "poll";
                    case ALBUM -> "album";
                    case MARKET -> "market";
                    case MARKET_ALBUM -> "market_album";
                    case WIKI -> "page";
                    default -> null;
                };
        if (prefix == null) {
            return null;
        }
        String owner = str(att.get("owner_id"));
        String itemId = str(att.get("item_id"));
        if (owner == null || itemId == null) {
            return fromCanonicalId(att, prefix);
        }
        String ref = prefix + owner + "_" + itemId;
        String accessKey = str(att.get("access_key"));
        return accessKey == null ? ref : ref + "_" + accessKey;
    }

    /** Legacy items only stored {@code id} = {@code type<owner>_<id>}. */
    private static String fromCanonicalId(Map<String, Object> att, String prefix) {
        String id = str(att.get("id"));
        if (id == null) {
            return null;
        }
        int digit = 0;
        while (digit < id.length() && !(id.charAt(digit) == '-' || Character.isDigit(id.charAt(digit)))) {
            digit++;
        }
        if (digit >= id.length() || !id.substring(digit).matches("-?\\d+_\\d+")) {
            return null;
        }
        return prefix + id.substring(digit);
    }

    public static List<String> kindsOf(Collection<Map<String, Object>> atts) {
        return atts == null
                ? List.of()
                : atts.stream().map(GrabbedAttachments::kindOf).toList();
    }

    static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
