package ru.posts.emulator.control;

import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.emulator.store.EmulatedPost;
import ru.posts.emulator.store.ModerationStore;
import ru.posts.emulator.store.Network;
import ru.posts.emulator.store.PostStore;
import ru.posts.emulator.vk.StoryTokenStore;
import ru.posts.emulator.vk.VkUploadSessionStore;
import ru.posts.emulator.vk.WallSourceStore;

@RestController
@RequestMapping("/_control")
public class ControlController {

    private final ScenarioService scenarioService;
    private final PostStore postStore;
    private final ModerationStore moderationStore;
    private final WallSourceStore wallSourceStore;
    private final StoryTokenStore storyTokenStore;
    private final VkUploadSessionStore uploadSessionStore;
    private final ru.posts.emulator.vk.VkCallbackServerStore callbackServerStore;
    private final ru.posts.emulator.telegram.TelegramWebhookStore webhookStore;
    private final ru.posts.emulator.max.MaxSubscriptionStore subscriptionStore;
    private final HttpHitCounter httpHitCounter;

    public ControlController(
            ScenarioService scenarioService,
            PostStore postStore,
            ModerationStore moderationStore,
            WallSourceStore wallSourceStore,
            StoryTokenStore storyTokenStore,
            VkUploadSessionStore uploadSessionStore,
            ru.posts.emulator.vk.VkCallbackServerStore callbackServerStore,
            ru.posts.emulator.telegram.TelegramWebhookStore webhookStore,
            ru.posts.emulator.max.MaxSubscriptionStore subscriptionStore,
            HttpHitCounter httpHitCounter) {
        this.scenarioService = scenarioService;
        this.postStore = postStore;
        this.moderationStore = moderationStore;
        this.wallSourceStore = wallSourceStore;
        this.storyTokenStore = storyTokenStore;
        this.uploadSessionStore = uploadSessionStore;
        this.callbackServerStore = callbackServerStore;
        this.webhookStore = webhookStore;
        this.subscriptionStore = subscriptionStore;
        this.httpHitCounter = httpHitCounter;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "social-emulator");
    }

    @GetMapping("/scenario")
    public ScenarioConfig getScenario() {
        return scenarioService.get();
    }

    @PutMapping("/scenario")
    public ScenarioConfig putScenario(@Valid @RequestBody ScenarioConfig config) {
        return scenarioService.update(config);
    }

    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestParam(name = "scenario", defaultValue = "true") boolean resetScenario) {
        postStore.reset();
        moderationStore.reset();
        wallSourceStore.reset();
        storyTokenStore.reset();
        uploadSessionStore.reset();
        callbackServerStore.reset();
        webhookStore.reset();
        subscriptionStore.reset();
        httpHitCounter.reset();
        if (resetScenario) {
            scenarioService.reset();
        }
        return Map.of("reset", true, "scenarioReset", resetScenario, "counts", postStore.counts());
    }

    /**
     * BE-33: seed VK suggested posts. Body: {@code {"ownerId":"-1","items":[{"id":10,"text":"..."}]}}
     * or {@code {"items":[{"owner_id":-1,"id":10,"text":"..."}]}}.
     */
    @PutMapping("/vk/suggests")
    public Map<String, Object> putVkSuggests(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                body.get("items") instanceof List<?> list ? (List<Map<String, Object>>) (List<?>) list : List.of();
        String ownerId = body.get("ownerId") != null
                ? String.valueOf(body.get("ownerId"))
                : body.get("owner_id") != null ? String.valueOf(body.get("owner_id")) : null;
        if (ownerId != null) {
            moderationStore.replaceSuggests(ownerId, items);
        } else {
            moderationStore.clearSuggests();
            for (Map<String, Object> item : items) {
                Object owner = item.get("owner_id") != null ? item.get("owner_id") : item.get("ownerId");
                Long id = item.get("id") instanceof Number n ? n.longValue() : null;
                moderationStore.putSuggest(owner == null ? "0" : String.valueOf(owner), id, item);
            }
        }
        return Map.of("count", items.size(), "suggests", moderationStore.snapshotSuggests());
    }

    @GetMapping("/vk/suggests")
    public Map<String, Object> listVkSuggests() {
        return moderationStore.snapshotSuggests();
    }

    /**
     * BE-33: seed comments. Body: {@code {"items":[{"network":"vk","ownerId":"-1","commentId":"5","type":"wall"}]}}.
     */
    @PutMapping("/comments")
    public Map<String, Object> putComments(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                body.get("items") instanceof List<?> list ? (List<Map<String, Object>>) (List<?>) list : List.of();
        moderationStore.replaceComments(items);
        return moderationStore.snapshotComments();
    }

    @GetMapping("/comments")
    public Map<String, Object> listComments() {
        return moderationStore.snapshotComments();
    }

    @GetMapping("/bans")
    public Map<String, Object> listBans() {
        return moderationStore.snapshotBans();
    }

    /**
     * Seed posts with optional createdAt (for Telegram 48h delete-window tests).
     * Body: {@code {"items":[{"network":"telegram","channelId":"-100","remotePostId":"1","createdAt":"2020-01-01T00:00:00Z"}]}}.
     */
    @PutMapping("/posts")
    public Map<String, Object> seedPosts(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                body.get("items") instanceof List<?> list ? (List<Map<String, Object>>) (List<?>) list : List.of();
        List<EmulatedPost> seeded = new java.util.ArrayList<>();
        for (Map<String, Object> item : items) {
            Network network = Network.from(String.valueOf(item.getOrDefault("network", "telegram")));
            String channelId = String.valueOf(item.getOrDefault("channelId", item.getOrDefault("channel_id", "0")));
            String remotePostId = item.get("remotePostId") != null
                    ? String.valueOf(item.get("remotePostId"))
                    : item.get("remote_post_id") != null ? String.valueOf(item.get("remote_post_id")) : null;
            String text = item.get("text") == null ? "" : String.valueOf(item.get("text"));
            java.time.Instant createdAt = null;
            if (item.get("createdAt") != null) {
                Object raw = item.get("createdAt");
                if (raw instanceof Number n) {
                    long v = n.longValue();
                    createdAt = v < 10_000_000_000L
                            ? java.time.Instant.ofEpochSecond(v)
                            : java.time.Instant.ofEpochMilli(v);
                } else {
                    createdAt = java.time.Instant.parse(String.valueOf(raw));
                }
            }
            seeded.add(postStore.seed(network, channelId, remotePostId, text, createdAt, Map.of()));
        }
        return Map.of("count", seeded.size(), "posts", seeded);
    }

    /**
     * QA-11 / TC-STORY-20: revoke VK community stories token so subsequent stories.* calls return
     * error_code=5 (auth failed).
     */
    @PostMapping("/vk/story-tokens/{groupId}/revoke")
    public Map<String, Object> revokeStoryToken(@org.springframework.web.bind.annotation.PathVariable String groupId) {
        storyTokenStore.revoke(groupId);
        return Map.of("groupId", groupId, "revoked", true, "revokedGroups", storyTokenStore.snapshot());
    }

    @PostMapping("/vk/story-tokens/{groupId}/restore")
    public Map<String, Object> restoreStoryToken(@org.springframework.web.bind.annotation.PathVariable String groupId) {
        storyTokenStore.restore(groupId);
        return Map.of("groupId", groupId, "revoked", false, "revokedGroups", storyTokenStore.snapshot());
    }

    @GetMapping("/vk/story-tokens")
    public Map<String, Object> listRevokedStoryTokens() {
        return Map.of("revokedGroups", storyTokenStore.snapshot());
    }

    /**
     * QA-12: seed VK wall source posts for grabber ingest ({@code wall.get}).
     * Body: {@code {"items":[{"id":1,"text":"...","marked_as_ads":0}]}}
     */
    @PutMapping("/vk/wall/{ownerId}")
    public Map<String, Object> putVkWall(
            @org.springframework.web.bind.annotation.PathVariable String ownerId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                body.get("items") instanceof List<?> list ? (List<Map<String, Object>>) (List<?>) list : List.of();
        wallSourceStore.replace(ownerId, items);
        return Map.of("ownerId", ownerId, "count", items.size(), "walls", wallSourceStore.snapshot());
    }

    @PostMapping("/vk/wall/{ownerId}/posts")
    public Map<String, Object> addVkWallPost(
            @org.springframework.web.bind.annotation.PathVariable String ownerId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> created = wallSourceStore.add(ownerId, body);
        return Map.of("ownerId", ownerId, "item", created);
    }

    @GetMapping("/vk/wall")
    public Map<String, Object> listVkWalls() {
        return wallSourceStore.snapshot();
    }

    @GetMapping("/posts")
    public List<EmulatedPost> posts(
            @RequestParam(required = false) String network,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) String contentType) {
        Network parsed = network == null || network.isBlank() ? null : Network.from(network);
        List<EmulatedPost> posts = postStore.list(parsed, channelId);
        if (contentType == null || contentType.isBlank()) {
            return posts;
        }
        boolean wantStory = "story".equalsIgnoreCase(contentType);
        return posts.stream()
                .filter(p -> {
                    Object ct = p.metadata() == null ? null : p.metadata().get("contentType");
                    boolean isStory = "story".equals(ct);
                    return wantStory == isStory;
                })
                .toList();
    }

    @GetMapping("/posts/count")
    public Map<String, Long> counts() {
        return postStore.counts();
    }

    @GetMapping("/duplicates")
    public Map<String, Object> duplicates() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("duplicateAttemptsByNetwork", postStore.duplicateAttemptCounts());
        body.put("trueDuplicates", postStore.findDuplicates());
        body.put("ok", postStore.findDuplicates().isEmpty());
        return body;
    }

    @GetMapping("/verify")
    public PostStore.VerificationReport verify() {
        return postStore.verify();
    }

    @GetMapping("/http-hits")
    public Map<String, Object> httpHits() {
        return httpHitCounter.snapshot();
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("counts", postStore.counts());
        body.put("duplicateAttemptsByNetwork", postStore.duplicateAttemptCounts());
        body.put("scenario", scenarioService.get());
        return ResponseEntity.ok(body);
    }
}
