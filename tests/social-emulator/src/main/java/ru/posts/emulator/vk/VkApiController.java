package ru.posts.emulator.vk;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
import ru.posts.emulator.store.Network;
import ru.posts.emulator.store.PostStore;
import ru.posts.emulator.web.PublishSupport;

@RestController
public class VkApiController {

    private final PostStore postStore;
    private final PublishSupport publishSupport;
    private final ConcurrentMap<String, UploadSession> uploads = new ConcurrentHashMap<>();
    private final AtomicLong photoSeq = new AtomicLong();

    public VkApiController(PostStore postStore, PublishSupport publishSupport) {
        this.postStore = postStore;
        this.publishSupport = publishSupport;
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
            @PathVariable String uploadId, @RequestPart(name = "photo", required = false) MultipartFile photo) {
        UploadSession session = uploads.get(uploadId);
        if (session == null) {
            return Map.of("error", "unknown upload id");
        }
        session.uploaded = true;
        session.server = "emulated";
        session.photo = photo == null ? "emulated-photo" : photo.getOriginalFilename();
        session.hash = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("server", 1);
        body.put("photo", session.photo);
        body.put("hash", session.hash);
        return body;
    }

    private ResponseEntity<?> dispatch(
            String method, MultiValueMap<String, String> params, HttpServletResponse rawResponse) throws IOException {
        String normalized = method == null ? "" : method.trim();
        return switch (normalized) {
            case "wall.post" -> wallPost(params, rawResponse);
            case "photos.getWallUploadServer" -> ResponseEntity.ok(getWallUploadServer(params));
            case "photos.saveWallPhoto" -> ResponseEntity.ok(saveWallPhoto(params));
            case "stories.getPhotoUploadServer", "stories.getVideoUploadServer" -> ResponseEntity.ok(
                    getStoryUploadServer(params, normalized.contains("Video")));
            case "stories.save" -> storiesSave(params, rawResponse);
            case "stories.delete" -> storiesDelete(params);
            case "wall.getById", "wall.get" -> ResponseEntity.ok(
                    Map.of("response", Map.of("items", java.util.List.of())));
            default -> ResponseEntity.ok(
                    Map.of("error", Map.of("error_code", 3, "error_msg", "Unknown method passed: " + normalized)));
        };
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
        String uploadId = UUID.randomUUID().toString();
        String groupId = first(params, "group_id", "owner_id");
        uploads.put(uploadId, new UploadSession(groupId));
        String uploadUrl = "http://localhost:8090/vk/upload/" + uploadId;
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

    private Map<String, Object> getStoryUploadServer(MultiValueMap<String, String> params, boolean video) {
        String uploadId = UUID.randomUUID().toString();
        String groupId = first(params, "group_id", "owner_id");
        UploadSession session = new UploadSession(groupId);
        session.linkText = first(params, "link_text");
        session.linkUrl = first(params, "link_url");
        session.guid = first(params, "guid");
        session.video = video;
        uploads.put(uploadId, session);
        String uploadUrl = "http://localhost:8090/vk/upload/" + uploadId;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("upload_url", uploadUrl);
        response.put("user_id", 1);
        return Map.of("response", response);
    }

    private ResponseEntity<?> storiesSave(MultiValueMap<String, String> params, HttpServletResponse rawResponse)
            throws IOException {
        return publishSupport.execute(Network.VK, rawResponse, () -> {
            String guid = first(params, "guid");
            String ownerId = first(params, "owner_id", "group_id");
            if (ownerId == null) {
                ownerId = "-1";
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("contentType", "story");
            metadata.put("link_text", first(params, "link_text"));
            metadata.put("link_url", first(params, "link_url"));
            // Prefer link params captured at getPhotoUploadServer when save body is opaque.
            for (UploadSession session : uploads.values()) {
                if (session.linkText != null) {
                    metadata.put("link_text", session.linkText);
                }
                if (session.linkUrl != null) {
                    metadata.put("link_url", session.linkUrl);
                }
                if (guid == null && session.guid != null) {
                    guid = session.guid;
                }
                if (session.groupId != null && (ownerId == null || "-1".equals(ownerId))) {
                    ownerId = session.groupId.startsWith("-") ? session.groupId : "-" + session.groupId;
                }
            }
            PostStore.CreateResult result = postStore.createOrGet(Network.VK, ownerId, guid, "story", metadata);
            long storyId;
            try {
                storyId = Long.parseLong(result.post().remotePostId());
            } catch (NumberFormatException ex) {
                storyId = photoSeq.incrementAndGet();
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", storyId);
            item.put("owner_id", Long.parseLong(ownerId.replace("-", "")) * -1);
            item.put("type", "photo");
            return ResponseEntity.ok(Map.of("response", Map.of("items", java.util.List.of(item))));
        });
    }

    private ResponseEntity<?> storiesDelete(MultiValueMap<String, String> params) {
        // Idempotent delete — always success for emulator.
        return ResponseEntity.ok(Map.of("response", 1));
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

    private static final class UploadSession {
        final String groupId;
        boolean uploaded;
        String server;
        String photo;
        String hash;
        String linkText;
        String linkUrl;
        String guid;
        boolean video;

        UploadSession(String groupId) {
            this.groupId = groupId;
        }
    }
}
