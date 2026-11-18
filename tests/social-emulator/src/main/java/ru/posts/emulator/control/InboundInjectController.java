package ru.posts.emulator.control;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import ru.posts.emulator.max.MaxSubscriptionStore;
import ru.posts.emulator.telegram.TelegramWebhookStore;
import ru.posts.emulator.vk.VkCallbackServerStore;

/**
 * Control endpoints that inject inbound social events into registered webhook URLs (BE-32).
 * Exists only in the social-emulator process ({@code /_control/**}).
 */
@RestController
@RequestMapping("/_control")
public class InboundInjectController {

    private final VkCallbackServerStore callbackServerStore;
    private final TelegramWebhookStore webhookStore;
    private final MaxSubscriptionStore subscriptionStore;
    private final RestClient http = RestClient.create();
    private final AtomicLong updateSeq = new AtomicLong(1);

    public InboundInjectController(
            VkCallbackServerStore callbackServerStore,
            TelegramWebhookStore webhookStore,
            MaxSubscriptionStore subscriptionStore) {
        this.callbackServerStore = callbackServerStore;
        this.webhookStore = webhookStore;
        this.subscriptionStore = subscriptionStore;
    }

    /**
     * Inject a VK Callback event for {@code groupId}. Emulator posts to the registered callback URL
     * with the stored secret and retries until the response body is {@code ok}.
     */
    @PostMapping(path = "/vk/callback/{groupId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> injectVk(@PathVariable String groupId, @RequestBody Map<String, Object> body) {
        List<VkCallbackServerStore.Server> servers = callbackServerStore.list(groupId);
        if (servers.isEmpty()) {
            return Map.of("ok", false, "error", "no_callback_server");
        }
        VkCallbackServerStore.Server server = servers.get(0);
        Map<String, Object> event = new LinkedHashMap<>(body);
        event.putIfAbsent("type", "wall_post_new");
        event.putIfAbsent("group_id", groupId);
        event.putIfAbsent("event_id", UUID.randomUUID().toString());
        event.put("secret", server.secret());
        if (!event.containsKey("object")) {
            Map<String, Object> object = new LinkedHashMap<>();
            object.put("id", updateSeq.incrementAndGet());
            object.put("from_id", -Long.parseLong(groupId.replace("-", "")));
            object.put("owner_id", -Long.parseLong(groupId.replace("-", "")));
            object.put("date", Instant.now().getEpochSecond());
            object.put("post_type", body.getOrDefault("post_type", "post"));
            object.put("text", body.getOrDefault("text", "hello"));
            event.put("object", object);
        }

        int attempts = 0;
        String last = null;
        for (int i = 0; i < 10; i++) {
            attempts++;
            try {
                ResponseEntity<String> response = http.post()
                        .uri(server.url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Retry-Counter", String.valueOf(i))
                        .body(event)
                        .retrieve()
                        .toEntity(String.class);
                last = response.getBody();
                if (response.getStatusCode().is2xxSuccessful() && "ok".equalsIgnoreCase(trim(last))) {
                    return Map.of("ok", true, "attempts", attempts, "url", server.url(), "response", last);
                }
                if (response.getStatusCode().value() == 503) {
                    Thread.sleep(200L * (i + 1));
                    continue;
                }
            } catch (Exception ex) {
                last = ex.toString();
                try {
                    Thread.sleep(200L * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return Map.of("ok", false, "attempts", attempts, "url", server.url(), "last", last == null ? "" : last);
    }

    @PostMapping(path = "/tg/update", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> injectTg(@RequestBody Map<String, Object> body) {
        String token = String.valueOf(body.getOrDefault("token", "emulator-token"));
        TelegramWebhookStore.Webhook wh = webhookStore.get(token);
        if (wh == null || wh.url() == null || wh.url().isBlank()) {
            return Map.of("ok", false, "error", "webhook_not_registered");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> update =
                body.get("update") instanceof Map<?, ?> m ? (Map<String, Object>) m : defaultTgUpdate(body);
        try {
            ResponseEntity<String> response = http.post()
                    .uri(wh.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Telegram-Bot-Api-Secret-Token", wh.secretToken() == null ? "" : wh.secretToken())
                    .body(update)
                    .retrieve()
                    .toEntity(String.class);
            return Map.of(
                    "ok",
                    response.getStatusCode().is2xxSuccessful(),
                    "status",
                    response.getStatusCode().value(),
                    "url",
                    wh.url());
        } catch (Exception ex) {
            return Map.of("ok", false, "error", ex.toString(), "url", wh.url());
        }
    }

    @PostMapping(path = "/max/update", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> injectMax(@RequestBody Map<String, Object> body) {
        String url = body.get("url") == null ? null : String.valueOf(body.get("url"));
        MaxSubscriptionStore.Subscription sub = url == null ? null : subscriptionStore.findByUrl(url);
        if (sub == null && url == null) {
            // pick first
            sub = subscriptionStore.list(String.valueOf(body.getOrDefault("token", ""))).stream()
                    .findFirst()
                    .orElse(null);
            if (sub != null) {
                url = sub.url();
            }
        }
        if (sub == null || url == null) {
            return Map.of("ok", false, "error", "subscription_not_found");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> update =
                body.get("update") instanceof Map<?, ?> m ? (Map<String, Object>) m : defaultMaxUpdate(body);
        try {
            ResponseEntity<String> response = http.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Max-Bot-Api-Secret", sub.secret() == null ? "" : sub.secret())
                    .body(update)
                    .retrieve()
                    .toEntity(String.class);
            return Map.of(
                    "ok",
                    response.getStatusCode().is2xxSuccessful(),
                    "status",
                    response.getStatusCode().value(),
                    "url",
                    url);
        } catch (Exception ex) {
            return Map.of("ok", false, "error", ex.toString(), "url", url);
        }
    }

    private Map<String, Object> defaultTgUpdate(Map<String, Object> body) {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("update_id", updateSeq.incrementAndGet());
        Map<String, Object> post = new LinkedHashMap<>();
        post.put("message_id", updateSeq.incrementAndGet());
        post.put("date", Instant.now().getEpochSecond());
        post.put("chat", Map.of("id", body.getOrDefault("chat_id", -1001), "type", "channel"));
        post.put("text", body.getOrDefault("text", "tg hello"));
        if (body.get("media_group_id") != null) {
            post.put("media_group_id", body.get("media_group_id"));
            post.put("photo", List.of(Map.of("file_id", "ph-" + updateSeq.incrementAndGet(), "file_size", 100)));
        }
        update.put("channel_post", post);
        return update;
    }

    private Map<String, Object> defaultMaxUpdate(Map<String, Object> body) {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("update_type", body.getOrDefault("update_type", "comment_created"));
        update.put("timestamp", Instant.now().toEpochMilli());
        update.put(
                "comment",
                Map.of(
                        "id",
                        String.valueOf(updateSeq.incrementAndGet()),
                        "text",
                        body.getOrDefault("text", "max comment"),
                        "sender",
                        Map.of("user_id", "u1")));
        return update;
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }
}
