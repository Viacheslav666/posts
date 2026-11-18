package ru.posts.emulator.vk;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.posts.emulator.config.EmulatorProperties;
import ru.posts.emulator.store.Network;
import ru.posts.emulator.store.PostStore;
import ru.posts.emulator.web.PublishSupport;

@RestController
public class VkApiController {

    private final PostStore postStore;
    private final PublishSupport publishSupport;
    private final WallSourceStore wallSourceStore;
    private final StoryTokenStore storyTokenStore;
    private final VkUploadSessionStore uploadSessions;
    private final EmulatorProperties emulatorProperties;
    private final AtomicLong photoSeq = new AtomicLong();

    public VkApiController(
            PostStore postStore,
            PublishSupport publishSupport,
            WallSourceStore wallSourceStore,
            StoryTokenStore storyTokenStore,
            VkUploadSessionStore uploadSessions,
            EmulatorProperties emulatorProperties) {
        this.postStore = postStore;
        this.publishSupport = publishSupport;
        this.wallSourceStore = wallSourceStore;
        this.storyTokenStore = storyTokenStore;
        this.uploadSessions = uploadSessions;
        this.emulatorProperties = emulatorProperties;
    }

    @GetMapping({"/method/{method}", "/vk/method/{method}"})
    public ResponseEntity<?> methodGet(
            @PathVariable String method,
            @RequestParam MultiValueMap<String, String> params,
            HttpServletResponse rawResponse)
            throws IOException {
        return dispatch(method, params, rawResponse);
    }

    @PostMapping(
            path = {"/method/{method}", "/vk/method/{method}"},
            consumes = {
                MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                MediaType.MULTIPART_FORM_DATA_VALUE,
                MediaType.ALL_VALUE
            })
    public ResponseEntity<?> methodPost(
            @PathVariable String method,
            @RequestParam MultiValueMap<String, String> params,
            HttpServletResponse rawResponse)
            throws IOException {
        return dispatch(method, params, rawResponse);
    }

    @PostMapping(
            path = {"/vk/upload/{uploadId}", "/upload/{uploadId}"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadPhoto(
            @PathVariable String uploadId,
            @RequestPart(name = "photo", required = false) MultipartFile photo,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestPart(name = "video_file", required = false) MultipartFile videoFile) {
        VkUploadSessionStore.Session session = uploadSessions.get(uploadId);
        if (session == null) {
            return Map.of("error", "unknown upload id");
        }
        MultipartFile payload = firstNonNull(videoFile, file, photo);
        session.uploaded = true;
        session.server = "emulated";
        session.photo = payload == null ? "emulated-photo" : payload.getOriginalFilename();
        session.hash = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("server", 1);
        body.put("photo", session.photo);
        body.put("hash", session.hash);
        if (session.video) {
            body.put("video_id", photoSeq.incrementAndGet());
        }
        return body;
    }

    private ResponseEntity<?> dispatch(
            String method, MultiValueMap<String, String> params, HttpServletResponse rawResponse) throws IOException {
        String normalized = method == null ? "" : method.trim();
        return switch (normalized) {
            case "wall.post" -> wallPost(params, rawResponse);
            case "photos.getWallUploadServer" -> ResponseEntity.ok(getWallUploadServer(params));
            case "photos.saveWallPhoto" -> ResponseEntity.ok(saveWallPhoto(params));
            case "stories.getPhotoUploadServer", "stories.getVideoUploadServer" -> storyUploadServer(
                    params, normalized.contains("Video"));
            case "stories.save" -> storiesSave(params, rawResponse);
            case "stories.delete" -> storiesDelete(params);
            case "wall.get" -> ResponseEntity.ok(wallGet(params));
            case "wall.getById" -> ResponseEntity.ok(Map.of("response", Map.of("items", java.util.List.of())));
            default -> ResponseEntity.ok(
                    Map.of("error", Map.of("error_code", 3, "error_msg", "Unknown method passed: " + normalized)));
        };
    }

    private Map<String, Object> wallGet(MultiValueMap<String, String> params) {
        String ownerId = first(params, "owner_id", "domain");
        String countRaw = first(params, "count");
        int count = 20;
        if (countRaw != null) {
            try {
                count = Integer.parseInt(countRaw);
            } catch (NumberFormatException ignored) {
                count = 20;
            }
        }
        return wallSourceStore.wallGet(ownerId == null ? "0" : ownerId, count);
    }

    private ResponseEntity<?> wallPost(MultiValueMap<String, String> params, HttpServletResponse rawResponse)
            throws IOException {
        return publishSupport.execute(Network.VK, rawResponse, () -> {
            String ownerId = first(params, "owner_id", "from_group");
            String message = first(params, "message", "text");
            String guid = first(params, "guid");
            String attachments = first(params, "attachments");

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("attachments", attachments);
            metadata.put("access_token_present", first(params, "access_token") != null);

            PostStore.CreateResult result =
                    postStore.createOrGet(Network.VK, ownerId == null ? "0" : ownerId, guid, message, metadata);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("post_id", Long.parseLong(result.post().remotePostId()));
            if (!result.created()) {
                response.put("post_already_exists", 1);
            }
            return ResponseEntity.ok(Map.of("response", response));
        });
    }

    private Map<String, Object> getWallUploadServer(MultiValueMap<String, String> params) {
        String groupId = first(params, "group_id", "owner_id");
        VkUploadSessionStore.Session session = uploadSessions.create(groupId);
        String uploadUrl = emulatorProperties.absolute("/vk/upload/" + session.uploadId());
        return Map.of("response", Map.of("upload_url", uploadUrl, "album_id", -1, "user_id", 1));
    }

    private Map<String, Object> saveWallPhoto(MultiValueMap<String, String> params) {
        long id = photoSeq.incrementAndGet();
        String ownerId = first(params, "group_id", "user_id", "owner_id");
        long owner = ownerId == null ? 1L : Long.parseLong(ownerId.replace("-", ""));
        Map<String, Object> photo = new LinkedHashMap<>();
        photo.put("id", id);
        photo.put("album_id", -7);
        photo.put("owner_id", -owner);
        photo.put("text", "");
        photo.put("access_key", UUID.randomUUID().toString().substring(0, 8));
        return Map.of("response", java.util.List.of(photo));
    }

    private ResponseEntity<?> storyUploadServer(MultiValueMap<String, String> params, boolean video) {
        ResponseEntity<?> revoked = storyAuthGuard(params);
        if (revoked != null) {
            return revoked;
        }
        return ResponseEntity.ok(getStoryUploadServer(params, video));
    }

    private Map<String, Object> getStoryUploadServer(MultiValueMap<String, String> params, boolean video) {
        String groupId = first(params, "group_id", "owner_id");
        VkUploadSessionStore.Session session = uploadSessions.create(groupId);
        session.linkText = first(params, "link_text");
        session.linkUrl = first(params, "link_url");
        session.guid = first(params, "guid");
        session.video = video;
        String uploadUrl = emulatorProperties.absolute("/vk/upload/" + session.uploadId());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("upload_url", uploadUrl);
        response.put("user_id", 1);
        return Map.of("response", response);
    }

    private ResponseEntity<?> storiesSave(MultiValueMap<String, String> params, HttpServletResponse rawResponse)
            throws IOException {
        ResponseEntity<?> revoked = storyAuthGuard(params);
        if (revoked != null) {
            return revoked;
        }
        return publishSupport.execute(Network.VK, rawResponse, () -> {
            String guid = first(params, "guid");
            // Resolve upload session by guid only — never scan all sessions (leaks group_id across ITs).
            VkUploadSessionStore.Session session = uploadSessions.findByGuid(guid);

            String ownerId = first(params, "owner_id", "group_id");
            if (ownerId == null && session != null && session.groupId != null) {
                ownerId = session.groupId;
            }
            if (ownerId == null) {
                ownerId = "0";
            }
            // Normalize to digits (channel external_id in publisher ITs is without leading '-').
            String channelId = ownerId.startsWith("-") ? ownerId.substring(1) : ownerId;

            boolean video = session != null && session.video;
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("contentType", "story");
            String linkText = first(params, "link_text");
            String linkUrl = first(params, "link_url");
            if (linkText == null && session != null) {
                linkText = session.linkText;
            }
            if (linkUrl == null && session != null) {
                linkUrl = session.linkUrl;
            }
            metadata.put("link_text", linkText);
            metadata.put("link_url", linkUrl);
            metadata.put("mediaKind", video ? "video" : "photo");

            PostStore.CreateResult result = postStore.createOrGet(Network.VK, channelId, guid, "story", metadata);
            long storyId;
            try {
                storyId = Long.parseLong(result.post().remotePostId());
            } catch (NumberFormatException ex) {
                storyId = photoSeq.incrementAndGet();
            }
            long ownerNumeric = Long.parseLong(channelId.replace("-", ""));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", storyId);
            item.put("owner_id", -ownerNumeric);
            item.put("type", video ? "video" : "photo");
            return ResponseEntity.ok(Map.of("response", Map.of("items", java.util.List.of(item))));
        });
    }

    private ResponseEntity<?> storiesDelete(MultiValueMap<String, String> params) {
        ResponseEntity<?> revoked = storyAuthGuard(params);
        if (revoked != null) {
            return revoked;
        }
        return ResponseEntity.ok(Map.of("response", 1));
    }

    private ResponseEntity<?> storyAuthGuard(MultiValueMap<String, String> params) {
        String groupId = first(params, "group_id", "owner_id");
        if (groupId != null && storyTokenStore.isRevoked(groupId)) {
            return ResponseEntity.ok(Map.of(
                    "error",
                    Map.of(
                            "error_code",
                            5,
                            "error_msg",
                            "User authorization failed: story token revoked",
                            "request_params",
                            java.util.List.of())));
        }
        return null;
    }

    private static MultipartFile firstNonNull(MultipartFile... files) {
        if (files == null) {
            return null;
        }
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                return file;
            }
        }
        for (MultipartFile file : files) {
            if (file != null) {
                return file;
            }
        }
        return null;
    }

    private static String first(MultiValueMap<String, String> params, String... names) {
        for (String name : names) {
            String value = params.getFirst(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
