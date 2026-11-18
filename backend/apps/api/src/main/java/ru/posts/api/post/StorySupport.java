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

/** Validation and helpers for content_kind=story (BE-16). */
public final class StorySupport {

    private static final Set<ApiEnums.Network> SUPPORTED = EnumSet.of(ApiEnums.Network.vk, ApiEnums.Network.ok);
    private static final long VK_VIDEO_MAX_BYTES = 350L * 1024L * 1024L;
    private static final Set<String> ALLOWED_PHOTO_MIME =
            Set.of("image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "image/heif", "image/heic");

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

    public static void assertMedia(PostDtos.PostContent content, List<MediaAssetEntity> assets) {
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
        if (video && asset.getSizeBytes() != null && asset.getSizeBytes() > VK_VIDEO_MAX_BYTES) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.STORY_MEDIA_INVALID,
                    "VK story video must be ≤ 350 MB");
        }
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
            if (story.warnings() != null) {
                warnings.addAll(story.warnings());
            }
        }
        if (mediaKind != null) {
            map.put("mediaKind", mediaKind);
        }
        if (!warnings.isEmpty()) {
            map.put("warnings", warnings);
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
                null);
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
}
