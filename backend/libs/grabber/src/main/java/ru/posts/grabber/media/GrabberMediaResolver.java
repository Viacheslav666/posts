package ru.posts.grabber.media;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.config.GrabberProperties;
import ru.posts.grabber.token.GrabberTokenProvider;
import ru.posts.persistence.entity.AutomationRuleEntity;
import ru.posts.persistence.entity.ChannelEntity;

/**
 * Turns the (already filtered / stripped) attachments of a grabbed post into VK {@code wall.post}
 * attachment strings for the target community:
 *
 * <ul>
 *   <li>photos are downloaded and re-uploaded to the target group (falls back to referencing the
 *       source photo if the re-upload fails);
 *   <li>videos are re-uploaded when "Перезалить видео" is on (public, ≤ 50 MB); otherwise — and for
 *       clips — the source video is referenced. A failed required re-upload blocks publication;
 *   <li>audio, playlists, docs, polls, albums, goods, collections and wiki pages are referenced;
 *       links/articles are attached by URL (VK accepts one link per post);
 *   <li>"Свои вложения" are used only when nothing else is attached.
 * </ul>
 */
@Component
@Profile("!openapi")
public class GrabberMediaResolver {

    private static final Logger log = LoggerFactory.getLogger(GrabberMediaResolver.class);
    private static final List<String> MP4_QUALITIES =
            List.of("mp4_2160", "mp4_1440", "mp4_1080", "mp4_720", "mp4_480", "mp4_360", "mp4_240", "mp4_144");

    private final VkMediaClient vk;
    private final GrabberTokenProvider tokenProvider;
    private final GrabberProperties properties;

    public GrabberMediaResolver(VkMediaClient vk, GrabberTokenProvider tokenProvider, GrabberProperties properties) {
        this.vk = vk;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
    }

    public Resolution resolve(
            AutomationRuleEntity rule, ChannelEntity channel, List<Map<String, Object>> attachments, String text) {
        Map<String, Object> config = rule.getConfig();
        if (channel == null || !"vk".equalsIgnoreCase(channel.getNetwork())) {
            return Resolution.ok(List.of(), false);
        }
        List<Map<String, Object>> atts = attachments == null ? List.of() : attachments;
        boolean reuploadVideo = GrabberConfigSupport.reuploadVideo(config);
        Optional<String> uploadToken = atts.isEmpty()
                ? Optional.empty()
                : tokenProvider.resolveTargetUploadToken(rule.getWorkspaceId(), channel.getId());

        Set<String> refs = new LinkedHashSet<>();
        boolean linkAdded = false;
        for (Map<String, Object> att : atts) {
            String kind = GrabbedAttachments.kindOf(att);
            String ref;
            switch (kind) {
                case GrabbedAttachments.PHOTO -> ref = photo(rule, channel, att, uploadToken);
                case GrabbedAttachments.VIDEO -> {
                    if (reuploadVideo) {
                        try {
                            ref = reuploadVideo(rule, channel, att, uploadToken);
                        } catch (VkMediaClient.MediaException ex) {
                            log.warn(
                                    "Grabber video re-upload failed ruleId={} video={} code={}: {}",
                                    rule.getId(),
                                    GrabbedAttachments.reference(att),
                                    ex.code(),
                                    ex.getMessage());
                            return Resolution.failed("video_reupload_failed", ex.code(), ex.getMessage());
                        }
                    } else {
                        ref = GrabbedAttachments.reference(att);
                    }
                }
                case GrabbedAttachments.LINK, GrabbedAttachments.ARTICLE -> {
                    if (linkAdded) {
                        continue;
                    }
                    ref = GrabbedAttachments.reference(att);
                    linkAdded = ref != null;
                }
                default -> ref = GrabbedAttachments.reference(att);
            }
            if (ref == null) {
                log.info(
                        "Grabber attachment skipped (unsupported / incomplete) ruleId={} type={}",
                        rule.getId(),
                        att.get("type"));
                continue;
            }
            refs.add(ref);
        }

        List<String> out = new ArrayList<>(refs);
        if (out.isEmpty()) {
            out.addAll(UserAttachmentsSpec.resolve(
                    GrabberConfigSupport.userAttachments(config), text, ThreadLocalRandom.current()));
        }
        if (out.size() > GrabbedAttachments.VK_MAX_ATTACHMENTS) {
            log.info(
                    "Grabber post has {} attachments, VK allows {} — extra dropped ruleId={}",
                    out.size(),
                    GrabbedAttachments.VK_MAX_ATTACHMENTS,
                    rule.getId());
            out = new ArrayList<>(out.subList(0, GrabbedAttachments.VK_MAX_ATTACHMENTS));
        }
        long visual = out.stream()
                .filter(r -> r.startsWith("photo") || r.startsWith("video"))
                .count();
        boolean carousel = GrabberConfigSupport.carouselMode(config) && visual >= 2;
        return Resolution.ok(List.copyOf(out), carousel);
    }

    private String photo(
            AutomationRuleEntity rule, ChannelEntity channel, Map<String, Object> att, Optional<String> token) {
        String fallback = GrabbedAttachments.reference(att);
        String url = GrabbedAttachments.str(att.get("url"));
        if (!properties.getMedia().isReuploadPhotos() || url == null || token.isEmpty()) {
            return fallback;
        }
        try {
            byte[] bytes = vk.download(url, properties.getMedia().getMaxPhotoBytes());
            return vk.uploadWallPhoto(channel.getExternalId(), token.get(), bytes, "photo.jpg");
        } catch (VkMediaClient.MediaException ex) {
            log.warn(
                    "Grabber photo re-upload failed, referencing source ruleId={} photo={} code={}: {}",
                    rule.getId(),
                    fallback,
                    ex.code(),
                    ex.getMessage());
            return fallback;
        }
    }

    private String reuploadVideo(
            AutomationRuleEntity rule, ChannelEntity channel, Map<String, Object> att, Optional<String> uploadToken) {
        if (GrabbedAttachments.str(att.get("platform")) != null) {
            throw new VkMediaClient.MediaException(
                    "video_external", "Видео размещено на внешней платформе: " + att.get("platform"));
        }
        if (Boolean.TRUE.equals(att.get("is_private"))) {
            throw new VkMediaClient.MediaException("video_private", "Видео не является публичным");
        }
        String owner = GrabbedAttachments.str(att.get("owner_id"));
        String id = GrabbedAttachments.str(att.get("item_id"));
        if (owner == null || id == null) {
            throw new VkMediaClient.MediaException("video_unavailable", "Нет идентификатора видео");
        }
        if (uploadToken.isEmpty()) {
            throw new VkMediaClient.MediaException("no_token", "Нет токена целевого сообщества для загрузки");
        }
        String readToken = tokenProvider
                .resolveVkToken(rule.getWorkspaceId(), rule.getTargetChannelId())
                .orElse(uploadToken.get());
        JsonNode video = vk.getVideo(owner, id, GrabbedAttachments.str(att.get("access_key")), readToken)
                .orElseThrow(() -> new VkMediaClient.MediaException(
                        "video_unavailable", "VK video.get не вернул видео " + owner + "_" + id));
        if (video.path("is_private").asInt(0) == 1) {
            throw new VkMediaClient.MediaException("video_private", "Видео не является публичным");
        }
        if (video.hasNonNull("platform") || video.path("files").has("external")) {
            throw new VkMediaClient.MediaException("video_external", "Видео размещено на внешней платформе");
        }
        List<String> urls = MP4_QUALITIES.stream()
                .map(q -> video.path("files").path(q).asText(""))
                .filter(u -> !u.isBlank())
                .toList();
        if (urls.isEmpty()) {
            throw new VkMediaClient.MediaException("video_not_downloadable", "VK не отдал файл видео (нет mp4)");
        }
        long max = properties.getMedia().getMaxVideoBytes();
        byte[] bytes = null;
        VkMediaClient.MediaException lastError = null;
        for (String url : urls) {
            try {
                bytes = vk.download(url, max);
                break;
            } catch (VkMediaClient.MediaException ex) {
                lastError = ex;
            }
        }
        if (bytes == null) {
            if (lastError != null && "too_large".equals(lastError.code())) {
                throw new VkMediaClient.MediaException(
                        "video_too_large", "Видео больше " + (max / (1024 * 1024)) + " МБ", lastError);
            }
            throw lastError == null
                    ? new VkMediaClient.MediaException("video_not_downloadable", "Не удалось скачать видео")
                    : lastError;
        }
        String title = Optional.ofNullable(
                        GrabbedAttachments.str(video.path("title").asText(null)))
                .orElse(GrabbedAttachments.str(att.get("title")));
        return vk.uploadVideo(
                channel.getExternalId(),
                uploadToken.get(),
                bytes,
                title,
                video.path("description").asText(""));
    }

    /** Result: VK attachment strings, carousel flag, or a failure that blocks publication. */
    public record Resolution(
            List<String> attachments,
            boolean carousel,
            String failureReason,
            String failureCode,
            String failureMessage) {

        static Resolution ok(List<String> attachments, boolean carousel) {
            return new Resolution(attachments, carousel, null, null, null);
        }

        static Resolution failed(String reason, String code, String message) {
            return new Resolution(List.of(), false, reason, code, message);
        }

        public boolean failed() {
            return failureReason != null;
        }
    }
}
