package ru.posts.emulator.vk;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.posts.emulator.config.EmulatorProperties;
import ru.posts.emulator.store.EmulatedPost;
import ru.posts.emulator.store.ModerationStore;
import ru.posts.emulator.store.Network;
import ru.posts.emulator.store.PostStore;
import ru.posts.emulator.web.PublishSupport;

@RestController
public class VkApiController {

    private static final Pattern EXECUTE_API_CALL =
            Pattern.compile("API\\.([a-zA-Z0-9_.]+)\\s*\\(\\s*(\\{[^}]*\\}|)\\s*\\)");

    private final PostStore postStore;
    private final ModerationStore moderationStore;
    private final PublishSupport publishSupport;
    private final WallSourceStore wallSourceStore;
    private final StoryTokenStore storyTokenStore;
    private final VkUploadSessionStore uploadSessions;
    private final EmulatorProperties emulatorProperties;
    private final AtomicLong photoSeq = new AtomicLong();

    public VkApiController(
            PostStore postStore,
            ModerationStore moderationStore,
            PublishSupport publishSupport,
            WallSourceStore wallSourceStore,
            StoryTokenStore storyTokenStore,
            VkUploadSessionStore uploadSessions,
            EmulatorProperties emulatorProperties) {
        this.postStore = postStore;
        this.moderationStore = moderationStore;
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
        if (payload != null) {
            try {
                byte[] bytes = payload.getBytes();
                session.contentSize = bytes.length;
                session.contentSha256 = sha256Hex(bytes);
            } catch (IOException ex) {
                session.contentSha256 = null;
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("server", 1);
        body.put("photo", session.photo);
        body.put("hash", session.hash);
        body.put("file", "doc-file-" + uploadId);
        body.put("success", true);
        if (session.contentSha256 != null) {
            body.put("content_sha256", session.contentSha256);
            body.put("content_size", session.contentSize);
        }
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
            case "wall.delete" -> moderation(rawResponse, () -> wallDelete(params));
            case "wall.pin", "wall.unpin" -> ResponseEntity.ok(Map.of("response", 1));
            case "wall.createComment" -> moderation(rawResponse, () -> wallCreateComment(params));
            case "wall.deleteComment" -> moderation(rawResponse, () -> deleteComment("wall", params));
            case "wall.restoreComment" -> moderation(rawResponse, () -> restoreComment("wall", params));
            case "photos.deleteComment" -> moderation(rawResponse, () -> deleteComment("photo", params));
            case "photos.restoreComment" -> moderation(rawResponse, () -> restoreComment("photo", params));
            case "video.deleteComment" -> moderation(rawResponse, () -> deleteComment("video", params));
            case "video.restoreComment" -> moderation(rawResponse, () -> restoreComment("video", params));
            case "market.deleteComment" -> moderation(rawResponse, () -> deleteComment("market", params));
            case "market.restoreComment" -> moderation(rawResponse, () -> restoreComment("market", params));
            case "board.deleteComment" -> moderation(rawResponse, () -> deleteComment("board", params));
            case "board.restoreComment" -> moderation(rawResponse, () -> restoreComment("board", params));
            case "groups.ban" -> moderation(rawResponse, () -> groupsBan(params));
            case "groups.unban" -> moderation(rawResponse, () -> groupsUnban(params));
            case "messages.send" -> moderation(rawResponse, () -> messagesSend(params));
            case "messages.isMessagesFromGroupAllowed" -> moderation(rawResponse, () -> messagesAllowed(params));
            case "execute" -> moderation(rawResponse, () -> executeBatch(params));
            case "photos.getWallUploadServer" -> ResponseEntity.ok(getWallUploadServer(params));
            case "photos.saveWallPhoto" -> ResponseEntity.ok(saveWallPhoto(params));
            case "video.save" -> ResponseEntity.ok(videoSave(params));
            case "docs.getWallUploadServer" -> ResponseEntity.ok(getDocUploadServer(params));
            case "docs.save" -> ResponseEntity.ok(docsSave(params));
            case "polls.create" -> ResponseEntity.ok(pollsCreate(params));
            case "stories.getPhotoUploadServer", "stories.getVideoUploadServer" -> storyUploadServer(
                    params, normalized.contains("Video"));
            case "stories.save" -> storiesSave(params, rawResponse);
            case "stories.delete" -> storiesDelete(params);
            case "wall.get" -> ResponseEntity.ok(wallGet(params));
            case "wall.getById" -> ResponseEntity.ok(wallGetById(params));
            default -> ResponseEntity.ok(
                    Map.of("error", Map.of("error_code", 3, "error_msg", "Unknown method passed: " + normalized)));
        };
    }

    private ResponseEntity<?> moderation(
            HttpServletResponse rawResponse, java.util.function.Supplier<ResponseEntity<?>> success)
            throws IOException {
        return publishSupport.execute(Network.VK, rawResponse, success);
    }

    private Map<String, Object> wallGet(MultiValueMap<String, String> params) {
        String ownerId = first(params, "owner_id", "domain");
        String filter = first(params, "filter");
        String countRaw = first(params, "count");
        int count = 20;
        if (countRaw != null) {
            try {
                count = Integer.parseInt(countRaw);
            } catch (NumberFormatException ignored) {
                count = 20;
            }
        }
        String owner = ownerId == null ? "0" : ownerId;
        if (filter != null && filter.equalsIgnoreCase("suggests")) {
            List<ModerationStore.SuggestedPost> suggests = moderationStore.listSuggests(owner);
            int limit = count <= 0 ? suggests.size() : Math.min(count, suggests.size());
            List<Map<String, Object>> items = suggests.stream()
                    .limit(limit)
                    .map(ModerationStore.SuggestedPost::toWallItem)
                    .toList();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("count", suggests.size());
            response.put("items", items);
            return Map.of("response", response);
        }
        return wallSourceStore.wallGet(owner, count);
    }

    private Map<String, Object> wallGetById(MultiValueMap<String, String> params) {
        String postsParam = first(params, "posts", "post_ids");
        List<Map<String, Object>> items = new ArrayList<>();
        if (postsParam != null && !postsParam.isBlank()) {
            for (String token : postsParam.split(",")) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String ownerId;
                String postId;
                int underscore = trimmed.lastIndexOf('_');
                if (underscore > 0) {
                    ownerId = trimmed.substring(0, underscore);
                    postId = trimmed.substring(underscore + 1);
                } else {
                    ownerId = first(params, "owner_id");
                    postId = trimmed;
                }
                Optional<EmulatedPost> found = postStore.findByRemoteId(Network.VK, ownerId, postId);
                if (found.isPresent()) {
                    EmulatedPost post = found.get();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", Long.parseLong(post.remotePostId()));
                    item.put(
                            "owner_id",
                            post.channelId() == null
                                    ? 0L
                                    : Long.parseLong(post.channelId().replace("+", "")));
                    item.put("text", post.text() == null ? "" : post.text());
                    item.put("date", post.createdAt().getEpochSecond());
                    items.add(item);
                } else {
                    // Also check wall source fixtures.
                    Map<String, Object> wall = wallSourceStore.wallGet(ownerId == null ? "0" : ownerId, 100);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = (Map<String, Object>) wall.get("response");
                    if (response != null && response.get("items") instanceof List<?> list) {
                        for (Object raw : list) {
                            if (raw instanceof Map<?, ?> m
                                    && String.valueOf(m.get("id")).equals(postId)) {
                                Map<String, Object> item = new LinkedHashMap<>();
                                m.forEach((k, v) -> item.put(String.valueOf(k), v));
                                items.add(item);
                            }
                        }
                    }
                }
            }
        }
        return Map.of("response", Map.of("items", items));
    }

    private ResponseEntity<?> wallPost(MultiValueMap<String, String> params, HttpServletResponse rawResponse)
            throws IOException {
        return publishSupport.execute(Network.VK, rawResponse, () -> {
            String ownerId = first(params, "owner_id", "from_group");
            String message = first(params, "message", "text");
            String guid = first(params, "guid");
            String attachments = first(params, "attachments");
            String postIdParam = first(params, "post_id");

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("attachments", attachments);
            metadata.put("from_group", first(params, "from_group"));
            metadata.put("signed", first(params, "signed"));
            metadata.put("mute_notifications", first(params, "mute_notifications"));
            metadata.put("close_comments", first(params, "close_comments"));
            metadata.put("copyright", first(params, "copyright"));
            metadata.put("lat", first(params, "lat"));
            metadata.put("long", first(params, "long"));
            metadata.put("donut_paid_duration", first(params, "donut_paid_duration"));
            metadata.put("primary_attachments_mode", first(params, "primary_attachments_mode"));
            metadata.put("guid", guid);
            metadata.put("access_token_present", first(params, "access_token") != null);

            String owner = ownerId == null ? "0" : ownerId;

            // BE-33: wall.post with post_id publishes a suggested post.
            if (postIdParam != null && !postIdParam.isBlank()) {
                long suggestId = Long.parseLong(postIdParam);
                Optional<ModerationStore.SuggestedPost> suggest = moderationStore.removeSuggest(owner, suggestId);
                metadata.put("published_suggest_id", suggestId);
                metadata.put("contentType", "suggest_published");
                String text = message;
                if ((text == null || text.isBlank()) && suggest.isPresent()) {
                    Object suggestText = suggest.get().fields().get("text");
                    text = suggestText == null ? "" : String.valueOf(suggestText);
                }
                // Keep the same post id as the suggest when publishing.
                EmulatedPost post = postStore.seed(Network.VK, owner, postIdParam, text, null, metadata);
                Map<String, Object> wallItem = new LinkedHashMap<>();
                wallItem.put("id", Long.parseLong(post.remotePostId()));
                wallItem.put("text", text == null ? "" : text);
                wallItem.put("date", System.currentTimeMillis() / 1000L);
                wallItem.put("post_type", "post");
                wallSourceStore.add(owner, wallItem);

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("post_id", Long.parseLong(post.remotePostId()));
                return ResponseEntity.ok(Map.of("response", response));
            }

            PostStore.CreateResult result = postStore.createOrGet(Network.VK, owner, guid, message, metadata);

            // Mirror into wall.get fixtures so API-mode findExisting works against the emulator.
            Map<String, Object> wallItem = new LinkedHashMap<>();
            wallItem.put("id", Long.parseLong(result.post().remotePostId()));
            wallItem.put("text", message == null ? "" : message);
            wallItem.put("date", System.currentTimeMillis() / 1000L);
            wallSourceStore.add(owner, wallItem);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("post_id", Long.parseLong(result.post().remotePostId()));
            if (!result.created()) {
                response.put("post_already_exists", 1);
            }
            return ResponseEntity.ok(Map.of("response", response));
        });
    }

    private ResponseEntity<?> wallDelete(MultiValueMap<String, String> params) {
        String ownerId = first(params, "owner_id");
        String postId = first(params, "post_id");
        postStore.deleteByRemoteId(Network.VK, ownerId, postId);
        if (postId != null) {
            try {
                moderationStore.removeSuggest(ownerId == null ? "0" : ownerId, Long.parseLong(postId));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        return ResponseEntity.ok(Map.of("response", 1));
    }

    private ResponseEntity<?> wallCreateComment(MultiValueMap<String, String> params) {
        String ownerId = first(params, "owner_id");
        String text = first(params, "message", "text");
        String replyTo = first(params, "reply_to_comment");
        ModerationStore.EmulatedComment comment =
                moderationStore.putComment(Network.VK, ownerId, null, "wall", text, null, false);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("comment_id", Long.parseLong(comment.commentId()));
        response.put("parents_stack", List.of());
        if (replyTo != null) {
            response.put("reply_to_comment", replyTo);
        }
        return ResponseEntity.ok(Map.of("response", response));
    }

    private ResponseEntity<?> deleteComment(String type, MultiValueMap<String, String> params) {
        String ownerId = first(params, "owner_id", "group_id");
        String commentId = first(params, "comment_id");
        if (commentId == null) {
            return ResponseEntity.ok(Map.of(
                    "error",
                    Map.of("error_code", 100, "error_msg", "comment_id is required", "request_params", List.of())));
        }
        moderationStore.deleteComment(Network.VK, ownerId, commentId, type);
        return ResponseEntity.ok(Map.of("response", 1));
    }

    private ResponseEntity<?> restoreComment(String type, MultiValueMap<String, String> params) {
        String ownerId = first(params, "owner_id", "group_id");
        String commentId = first(params, "comment_id");
        if (commentId == null) {
            return ResponseEntity.ok(Map.of(
                    "error",
                    Map.of("error_code", 100, "error_msg", "comment_id is required", "request_params", List.of())));
        }
        Optional<ModerationStore.EmulatedComment> restored =
                moderationStore.restoreComment(Network.VK, ownerId, commentId);
        if (restored.isEmpty()) {
            // Create as restored for idempotent restore of unknown comments.
            moderationStore.putComment(Network.VK, ownerId, commentId, type, "", null, false);
        }
        return ResponseEntity.ok(Map.of("response", 1));
    }

    private ResponseEntity<?> groupsBan(MultiValueMap<String, String> params) {
        // VK: group_id = community, owner_id = user being banned.
        String groupId = first(params, "group_id");
        String userId = first(params, "owner_id", "user_id");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("end_date", first(params, "end_date"));
        payload.put("reason", first(params, "reason"));
        payload.put("comment", first(params, "comment"));
        payload.put("comment_visible", first(params, "comment_visible"));
        moderationStore.ban(Network.VK, groupId, userId, payload);
        return ResponseEntity.ok(Map.of("response", 1));
    }

    private ResponseEntity<?> groupsUnban(MultiValueMap<String, String> params) {
        String groupId = first(params, "group_id");
        String userId = first(params, "owner_id", "user_id");
        moderationStore.unban(Network.VK, groupId, userId);
        return ResponseEntity.ok(Map.of("response", 1));
    }

    private ResponseEntity<?> messagesSend(MultiValueMap<String, String> params) {
        long messageId = photoSeq.incrementAndGet();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message_id", messageId);
        String peerId = first(params, "peer_id", "user_id");
        if (peerId != null) {
            response.put("peer_id", peerId);
        }
        return ResponseEntity.ok(Map.of("response", messageId));
    }

    private ResponseEntity<?> messagesAllowed(MultiValueMap<String, String> params) {
        // Emulator defaults to allowed unless scenario forced an error earlier.
        return ResponseEntity.ok(Map.of("response", 1));
    }

    private ResponseEntity<?> executeBatch(MultiValueMap<String, String> params) {
        String code = first(params, "code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "error", Map.of("error_code", 100, "error_msg", "code is required", "request_params", List.of())));
        }
        Matcher matcher = EXECUTE_API_CALL.matcher(code);
        List<Object> results = new ArrayList<>();
        int count = 0;
        while (matcher.find() && count < 25) {
            String method = matcher.group(1);
            String argsJson = matcher.group(2);
            MultiValueMap<String, String> callParams = parseExecuteArgs(argsJson);
            // Carry over access_token from outer execute call.
            if (first(params, "access_token") != null && callParams.getFirst("access_token") == null) {
                callParams.add("access_token", first(params, "access_token"));
            }
            ResponseEntity<?> result = dispatchSync(method, callParams);
            Object body = result.getBody();
            if (body instanceof Map<?, ?> map && map.containsKey("response")) {
                results.add(map.get("response"));
            } else if (body instanceof Map<?, ?> map && map.containsKey("error")) {
                results.add(map);
            } else {
                results.add(body);
            }
            count++;
        }
        return ResponseEntity.ok(Map.of("response", results));
    }

    /** Execute nested API calls without re-entering scenario faults / HTTP layer. */
    private ResponseEntity<?> dispatchSync(String method, MultiValueMap<String, String> params) {
        String normalized = method == null ? "" : method.trim();
        return switch (normalized) {
            case "wall.delete" -> wallDelete(params);
            case "wall.createComment" -> wallCreateComment(params);
            case "wall.deleteComment" -> deleteComment("wall", params);
            case "wall.restoreComment" -> restoreComment("wall", params);
            case "photos.deleteComment" -> deleteComment("photo", params);
            case "photos.restoreComment" -> restoreComment("photo", params);
            case "video.deleteComment" -> deleteComment("video", params);
            case "video.restoreComment" -> restoreComment("video", params);
            case "market.deleteComment" -> deleteComment("market", params);
            case "market.restoreComment" -> restoreComment("market", params);
            case "board.deleteComment" -> deleteComment("board", params);
            case "board.restoreComment" -> restoreComment("board", params);
            case "groups.ban" -> groupsBan(params);
            case "groups.unban" -> groupsUnban(params);
            case "messages.send" -> messagesSend(params);
            case "messages.isMessagesFromGroupAllowed" -> messagesAllowed(params);
            case "wall.get" -> ResponseEntity.ok(wallGet(params));
            case "wall.getById" -> ResponseEntity.ok(wallGetById(params));
            case "wall.post" -> {
                // Nested wall.post without publishSupport (fault already applied on execute).
                try {
                    yield wallPostNested(params);
                } catch (Exception ex) {
                    yield ResponseEntity.ok(Map.of(
                            "error",
                            Map.of("error_code", 10, "error_msg", ex.getMessage(), "request_params", List.of())));
                }
            }
            default -> ResponseEntity.ok(
                    Map.of("error", Map.of("error_code", 3, "error_msg", "Unknown method passed: " + normalized)));
        };
    }

    private ResponseEntity<?> wallPostNested(MultiValueMap<String, String> params) {
        String ownerId = first(params, "owner_id", "from_group");
        String message = first(params, "message", "text");
        String guid = first(params, "guid");
        String postIdParam = first(params, "post_id");
        String owner = ownerId == null ? "0" : ownerId;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("guid", guid);
        metadata.put("from_group", first(params, "from_group"));
        metadata.put("signed", first(params, "signed"));
        if (postIdParam != null && !postIdParam.isBlank()) {
            long suggestId = Long.parseLong(postIdParam);
            Optional<ModerationStore.SuggestedPost> suggest = moderationStore.removeSuggest(owner, suggestId);
            String text = message;
            if ((text == null || text.isBlank()) && suggest.isPresent()) {
                Object suggestText = suggest.get().fields().get("text");
                text = suggestText == null ? "" : String.valueOf(suggestText);
            }
            EmulatedPost post = postStore.seed(Network.VK, owner, postIdParam, text, null, metadata);
            wallSourceStore.add(
                    owner, Map.of("id", Long.parseLong(post.remotePostId()), "text", text == null ? "" : text));
            return ResponseEntity.ok(Map.of("response", Map.of("post_id", Long.parseLong(post.remotePostId()))));
        }
        PostStore.CreateResult result = postStore.createOrGet(Network.VK, owner, guid, message, metadata);
        return ResponseEntity.ok(Map.of(
                "response", Map.of("post_id", Long.parseLong(result.post().remotePostId()))));
    }

    private static MultiValueMap<String, String> parseExecuteArgs(String argsJson) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (argsJson == null || argsJson.isBlank() || "{}".equals(argsJson.trim())) {
            return params;
        }
        // Simple key:value parser for API.method({owner_id: -1, comment_id: 5}) / JSON-ish.
        String body = argsJson.trim();
        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1);
        }
        if (body.isBlank()) {
            return params;
        }
        for (String part : body.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim().replace("\"", "").replace("'", "");
            String value = kv[1].trim().replace("\"", "").replace("'", "");
            if (!key.isEmpty()) {
                params.add(key, value);
            }
        }
        return params;
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

    private Map<String, Object> videoSave(MultiValueMap<String, String> params) {
        String groupId = first(params, "group_id", "owner_id");
        VkUploadSessionStore.Session session = uploadSessions.create(groupId);
        session.video = true;
        long videoId = photoSeq.incrementAndGet();
        long owner = groupId == null ? 1L : Long.parseLong(groupId.replace("-", ""));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("upload_url", emulatorProperties.absolute("/vk/upload/" + session.uploadId()));
        response.put("video_id", videoId);
        response.put("owner_id", -owner);
        return Map.of("response", response);
    }

    private Map<String, Object> getDocUploadServer(MultiValueMap<String, String> params) {
        String groupId = first(params, "group_id", "owner_id");
        VkUploadSessionStore.Session session = uploadSessions.create(groupId);
        return Map.of(
                "response", Map.of("upload_url", emulatorProperties.absolute("/vk/upload/" + session.uploadId())));
    }

    private Map<String, Object> docsSave(MultiValueMap<String, String> params) {
        long id = photoSeq.incrementAndGet();
        String ownerId = first(params, "group_id", "owner_id");
        long owner = ownerId == null ? 1L : Long.parseLong(ownerId.replace("-", ""));
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", id);
        doc.put("owner_id", -owner);
        doc.put("title", first(params, "title"));
        return Map.of("response", Map.of("type", "doc", "doc", doc));
    }

    private Map<String, Object> pollsCreate(MultiValueMap<String, String> params) {
        long id = photoSeq.incrementAndGet();
        String ownerId = first(params, "owner_id", "group_id");
        long owner = ownerId == null ? -1L : Long.parseLong(ownerId);
        Map<String, Object> poll = new LinkedHashMap<>();
        poll.put("id", id);
        poll.put("owner_id", owner);
        poll.put("question", first(params, "question"));
        return Map.of("response", poll);
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
            if (session != null && session.contentSha256 != null) {
                metadata.put("contentSha256", session.contentSha256);
                metadata.put("contentSize", session.contentSize);
            }

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

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            // SHA-256 is always available on the JDK; keep a deterministic non-truncating fallback.
            return "sha256-unavailable-" + bytes.length;
        }
    }
}
