package ru.posts.api.post;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.post.PostDtos;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.MediaAssetEntity;

/** Validation and helpers for content_kind=story (BE-16 / BE-24). */
public final class StorySupport {

    private static final Set<ApiEnums.Network> SUPPORTED = EnumSet.of(ApiEnums.Network.vk, ApiEnums.Network.ok);
    /** Product story video limit (stricter than VK API 350 MB). */
    private static final long STORY_VIDEO_MAX_BYTES = 50L * 1024L * 1024L;

    private static final long STORY_VIDEO_MAX_DURATION_MS = 60_000L;
    private static final int STORY_MIN_WIDTH = 720;
    private static final int STORY_MIN_HEIGHT = 1280;
    private static final double ASPECT_9_16 = 9.0 / 16.0;
    private static final double ASPECT_TOLERANCE = 0.03;
    private static final Set<String> ALLOWED_PHOTO_MIME =
            Set.of("image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "image/heif", "image/heic");
    private static final Set<String> H264_ALIASES = Set.of("h264", "avc", "avc1", "libx264");
    private static final Set<String> AAC_ALIASES = Set.of("aac", "mp4a");

    private StorySupport() {}

    public static boolean isStory(String contentKind) {
        return "story".equals(contentKind);
    }

    public static void assertSupportedNetworks(List<PostDtos.PostVariantUpsert> variants) {
        if (variants == null) {
            return;
        }
        for (PostDtos.PostVariantUpsert v : variants) {
            if (v.network() == null || !SUPPORTED.contains(v.network())) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.STORY_UNSUPPORTED_NETWORK,
                        "Stories are only supported for VK and OK; Telegram/Max/Instagram are out of scope");
            }
        }
    }

    public static void assertChannelsReady(List<ChannelEntity> channels) {
        for (ChannelEntity channel : channels) {
            if ("vk".equals(channel.getNetwork())) {
                if (!"ready".equals(channel.getStoryConnectionStatus())) {
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            ProblemCodes.STORY_CONNECTION_REQUIRED,
                            "VK channel requires stories connection (story_connection_status=ready)");
                }
            } else if ("tg".equals(channel.getNetwork())
                    || "max".equals(channel.getNetwork())
                    || "rutube".equals(channel.getNetwork())) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.STORY_UNSUPPORTED_NETWORK,
                        "Stories are not supported for network " + channel.getNetwork());
            }
            // OK: no separate story-connect required when channel is active.
            if ("ok".equals(channel.getNetwork()) && !"active".equals(channel.getStatus())) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.CHANNEL_REAUTH_REQUIRED,
                        "OK channel must be active to publish Moments");
            }
        }
    }

    /**
     * Validates story media. Returns soft warnings (e.g. near-miss aspect) that callers may persist
     * into {@code story_options.warnings}.
     */
    public static List<String> assertMedia(PostDtos.PostContent content, List<MediaAssetEntity> assets) {
        List<java.util.UUID> mediaIds = content == null || content.mediaIds() == null ? List.of() : content.mediaIds();
        if (mediaIds.size() != 1) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.STORY_MEDIA_INVALID,
                    "Story requires exactly one media asset");
        }
        if (assets == null || assets.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.STORY_MEDIA_INVALID, "Story media not found");
        }
        MediaAssetEntity asset = assets.getFirst();
        String mime = asset.getMime() == null ? "" : asset.getMime().toLowerCase(Locale.ROOT);
        boolean video = mime.startsWith("video/");
        boolean photo = ALLOWED_PHOTO_MIME.contains(mime);
        if (!video && !photo) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.STORY_MEDIA_INVALID,
                    "Story media must be JPG/PNG/GIF/WEBP/HEIF photo or video");
        }

        List<String> warnings = new ArrayList<>();
        Integer width = firstInt(asset.getWidth(), metaNumber(asset, "width"));
        Integer height = firstInt(asset.getHeight(), metaNumber(asset, "height"));
        Integer durationMs = firstInt(asset.getDurationMs(), metaNumber(asset, "durationMs"));
        Long sizeBytes = asset.getSizeBytes();
        if (sizeBytes == null) {
            Number metaSize = metaNumber(asset, "sizeBytes");
            if (metaSize != null) {
                sizeBytes = metaSize.longValue();
            }
        }

        if (width == null || height == null || width <= 0 || height <= 0) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.STORY_MEDIA_RESOLUTION,
                    "Story media must be processed with width/height (min 720×1280, 9:16)");
        }
        if (width < STORY_MIN_WIDTH || height < STORY_MIN_HEIGHT) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.STORY_MEDIA_RESOLUTION,
                    "Story media must be at least 720×1280 (got " + width + "×" + height + ")");
        }
        double aspect = width / (double) height;
        if (Math.abs(aspect - ASPECT_9_16) > ASPECT_TOLERANCE) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.STORY_MEDIA_ASPECT,
                    "Story media aspect ratio must be 9:16 (got " + width + "×" + height + ")");
        }
        if (Math.abs(aspect - ASPECT_9_16) > 0.01) {
            warnings.add("Story aspect is close but not exact 9:16; networks may letterbox");
        }

        if (video) {
            if (sizeBytes != null && sizeBytes > STORY_VIDEO_MAX_BYTES) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.STORY_MEDIA_SIZE, "Story video must be ≤ 50 MB");
            }
            if (durationMs != null && durationMs > STORY_VIDEO_MAX_DURATION_MS) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.STORY_MEDIA_DURATION,
                        "Story video must be ≤ 60 seconds");
            }
            if (durationMs == null) {
                warnings.add("Story video duration unknown; ensure ≤ 60 s before publish");
            }
            String videoCodec = metaString(asset, "videoCodec");
            String audioCodec = metaString(asset, "audioCodec");
            if (videoCodec == null || videoCodec.isBlank()) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.STORY_MEDIA_CODEC,
                        "Story video must declare H.264 codec in media metadata");
            }
            if (!isH264(videoCodec)) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.STORY_MEDIA_CODEC,
                        "Story video codec must be H.264 (got " + videoCodec + ")");
            }
            if (audioCodec != null && !audioCodec.isBlank() && !isAac(audioCodec)) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.STORY_MEDIA_CODEC,
                        "Story video audio codec must be AAC (got " + audioCodec + ")");
            }
            if (audioCodec == null || audioCodec.isBlank()) {
                warnings.add("Story video has no audio track; AAC is required when audio is present");
            }
        }
        return List.copyOf(warnings);
    }

    public static void assertLink(PostDtos.StoryOptions story, List<ApiEnums.Network> networks) {
        if (story == null) {
            return;
        }
        String url = story.linkUrl();
        String text = story.linkText();
        boolean hasUrl = url != null && !url.isBlank();
        boolean hasText = text != null && !text.isBlank();
        if (hasUrl != hasText) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.STORY_LINK_INVALID,
                    "story.linkText and story.linkUrl must be set together");
        }
        if (!hasUrl) {
            return;
        }
        boolean hasVk = networks != null && networks.contains(ApiEnums.Network.vk);
        if (hasVk && !isVkInternalUrl(url)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.STORY_LINK_INVALID,
                    "VK story link_url must be https://vk.com/… or https://vk.ru/…");
        }
        if (url.length() > 2048) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.STORY_LINK_INVALID,
                    "story.linkUrl max length is 2048");
        }
    }

    public static boolean isVkInternalUrl(String url) {
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String h = host.toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && (h.equals("vk.com") || h.equals("vk.ru") || h.endsWith(".vk.com") || h.endsWith(".vk.ru"));
        } catch (Exception ex) {
            return false;
        }
    }

    public static ApiEnums.StoryTtl resolveTtl(PostDtos.StoryOptions story) {
        if (story == null || story.ttl() == null) {
            return ApiEnums.StoryTtl.ttl_24h;
        }
        return story.ttl();
    }

    public static Map<String, Object> toStoryOptionsMap(
            PostDtos.StoryOptions story, List<ApiEnums.Network> networks, String mediaKind) {
        return toStoryOptionsMap(story, networks, mediaKind, List.of());
    }

    public static Map<String, Object> toStoryOptionsMap(
            PostDtos.StoryOptions story,
            List<ApiEnums.Network> networks,
            String mediaKind,
            List<String> mediaWarnings) {
        Map<String, Object> map = new LinkedHashMap<>();
        ApiEnums.StoryTtl ttl = resolveTtl(story);
        map.put("ttl", ttl.name());
        List<String> warnings = new ArrayList<>();
        boolean hasOk = networks != null && networks.contains(ApiEnums.Network.ok);
        if (hasOk && ttl != ApiEnums.StoryTtl.ttl_24h) {
            map.put("effectiveTtl", ApiEnums.StoryTtl.ttl_24h.name());
            warnings.add("OK Moments live ~24h; effective_ttl=ttl_24h will be applied");
        } else if (hasOk) {
            map.put("effectiveTtl", ApiEnums.StoryTtl.ttl_24h.name());
        }
        if (story != null) {
            if (story.linkText() != null) {
                map.put("linkText", story.linkText());
            }
            if (story.linkUrl() != null) {
                map.put("linkUrl", story.linkUrl());
            }
            if (story.autoDeleteAfterMinutes() != null) {
                map.put("autoDeleteAfterMinutes", story.autoDeleteAfterMinutes());
            }
            if (story.autoDeleteAt() != null) {
                map.put("autoDeleteAt", story.autoDeleteAt().toString());
            }
            if (story.deleteAfterPublish() != null) {
                map.put("deleteAfterPublish", story.deleteAfterPublish());
            }
            if (story.addToNews() != null) {
                map.put("addToNews", story.addToNews());
            } else {
                map.put("addToNews", true);
            }
            if (story.warnings() != null) {
                warnings.addAll(story.warnings());
            }
        } else {
            map.put("addToNews", true);
        }
        if (mediaWarnings != null) {
            warnings.addAll(mediaWarnings);
        }
        if (mediaKind != null) {
            map.put("mediaKind", mediaKind);
        }
        if (!warnings.isEmpty()) {
            map.put("warnings", warnings.stream().distinct().toList());
        }
        return map;
    }

    public static PostDtos.StoryOptions toDto(
            String ttl, String linkText, String linkUrl, Map<String, Object> options) {
        ApiEnums.StoryTtl parsedTtl = ApiEnums.StoryTtl.ttl_24h;
        if (ttl != null && !ttl.isBlank()) {
            try {
                parsedTtl = ApiEnums.StoryTtl.valueOf(ttl);
            } catch (IllegalArgumentException ignored) {
                // keep default
            }
        }
        ApiEnums.StoryTtl effective = null;
        String mediaKind = null;
        Integer autoDeleteAfterMinutes = null;
        Instant autoDeleteAt = null;
        Boolean deleteAfterPublish = null;
        Boolean addToNews = null;
        List<String> warnings = null;
        if (options != null) {
            Object eff = options.get("effectiveTtl");
            if (eff != null) {
                try {
                    effective = ApiEnums.StoryTtl.valueOf(String.valueOf(eff));
                } catch (IllegalArgumentException ignored) {
                    // ignore
                }
            }
            Object mk = options.get("mediaKind");
            if (mk != null) {
                mediaKind = String.valueOf(mk);
            }
            Object adm = options.get("autoDeleteAfterMinutes");
            if (adm instanceof Number n) {
                autoDeleteAfterMinutes = n.intValue();
            }
            Object ada = options.get("autoDeleteAt");
            if (ada != null) {
                try {
                    autoDeleteAt = Instant.parse(String.valueOf(ada));
                } catch (Exception ignored) {
                    // ignore
                }
            }
            Object dap = options.get("deleteAfterPublish");
            if (dap instanceof Boolean b) {
                deleteAfterPublish = b;
            }
            Object atn = options.get("addToNews");
            if (atn instanceof Boolean b) {
                addToNews = b;
            } else if (atn != null) {
                String s = String.valueOf(atn).trim().toLowerCase(Locale.ROOT);
                if ("true".equals(s) || "1".equals(s) || "false".equals(s) || "0".equals(s)) {
                    addToNews = "true".equals(s) || "1".equals(s);
                }
            }
            Object w = options.get("warnings");
            if (w instanceof List<?> list) {
                warnings = list.stream().map(String::valueOf).toList();
            }
        }
        return new PostDtos.StoryOptions(
                parsedTtl,
                linkText,
                linkUrl,
                mediaKind,
                autoDeleteAfterMinutes,
                autoDeleteAt,
                deleteAfterPublish,
                effective,
                warnings,
                null,
                addToNews);
    }

    public static Instant resolveAutoDeleteAt(PostDtos.StoryOptions story, Instant scheduledAt) {
        if (story == null) {
            return null;
        }
        if (story.autoDeleteAt() != null) {
            if (scheduledAt != null && story.autoDeleteAt().isBefore(scheduledAt)) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.VALIDATION_FAILED,
                        "autoDeleteAt must be after scheduledAt");
            }
            return story.autoDeleteAt();
        }
        if (story.autoDeleteAfterMinutes() != null && story.autoDeleteAfterMinutes() > 0) {
            Instant base = scheduledAt == null ? Instant.now() : scheduledAt;
            return base.plus(story.autoDeleteAfterMinutes(), ChronoUnit.MINUTES);
        }
        return null;
    }

    public static String mediaKindOf(MediaAssetEntity asset) {
        if (asset == null || asset.getMime() == null) {
            return "photo";
        }
        return asset.getMime().toLowerCase(Locale.ROOT).startsWith("video/") ? "video" : "photo";
    }

    private static boolean isH264(String codec) {
        String c = codec.toLowerCase(Locale.ROOT).replace('-', '_');
        for (String alias : H264_ALIASES) {
            if (c.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAac(String codec) {
        String c = codec.toLowerCase(Locale.ROOT);
        for (String alias : AAC_ALIASES) {
            if (c.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private static Integer firstInt(Integer primary, Number fallback) {
        if (primary != null) {
            return primary;
        }
        return fallback == null ? null : fallback.intValue();
    }

    private static Number metaNumber(MediaAssetEntity asset, String key) {
        if (asset.getMeta() == null) {
            return null;
        }
        Object v = asset.getMeta().get(key);
        if (v instanceof Number n) {
            return n;
        }
        if (v == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String metaString(MediaAssetEntity asset, String key) {
        if (asset.getMeta() == null) {
            return null;
        }
        Object v = asset.getMeta().get(key);
        return v == null ? null : String.valueOf(v);
    }
}
