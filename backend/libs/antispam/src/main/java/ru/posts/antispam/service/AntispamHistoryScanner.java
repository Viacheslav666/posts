package ru.posts.antispam.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import ru.posts.persistence.entity.AntispamRuleEntity;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.social.inbound.SocialEventKind;
import ru.posts.social.inbound.SocialInboundAuthor;
import ru.posts.social.inbound.SocialInboundEvent;

/**
 * Manual check of the latest VK wall comments. Runs them through the same engine as live events.
 * The base URL is the configured social host (emulator in tests); this client never defaults to api.vk.com.
 */
public class AntispamHistoryScanner {

    private final AntispamEventProcessor processor;
    private final RestClient restClient;
    private final String apiVersion;

    public AntispamHistoryScanner(AntispamEventProcessor processor, String baseUrl, String apiVersion) {
        this.processor = processor;
        this.apiVersion = apiVersion == null || apiVersion.isBlank() ? "5.199" : apiVersion;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(20));
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restClient =
                RestClient.builder().baseUrl(normalized).requestFactory(factory).build();
    }

    public int scan(AntispamRuleEntity rule, ChannelEntity channel, String accessToken, int limit) {
        int cap = Math.max(1, Math.min(limit, 100));
        JsonNode body = call("wall.getComments", form(accessToken, channel.getExternalId(), cap));
        JsonNode items = body.path("response").path("items");
        if (!items.isArray()) {
            return 0;
        }
        int scanned = 0;
        for (JsonNode item : items) {
            if (scanned >= cap) {
                break;
            }
            String commentId = item.path("id").asText(null);
            if (commentId == null || commentId.isBlank()) {
                continue;
            }
            String postId = item.path("post_id").asText("");
            String fromId = item.path("from_id").asText(null);
            SocialInboundEvent event = new SocialInboundEvent(
                    "vk",
                    channel.getId(),
                    channel.getWorkspaceId(),
                    SocialEventKind.COMMENT_NEW,
                    "scan:" + channel.getId() + ":" + commentId,
                    Map.of(
                            "owner_id",
                            channel.getExternalId() == null ? "" : channel.getExternalId(),
                            "post_id",
                            postId,
                            "comment_id",
                            commentId),
                    fromId == null ? null : new SocialInboundAuthor(fromId, fromId.startsWith("-"), false),
                    item.path("text").asText(""),
                    null,
                    List.of(),
                    Instant.now(),
                    Instant.now(),
                    "{\"type\":\"wall_reply_new\"}",
                    List.of("antispam"));
            processor.process(event);
            scanned++;
        }
        return scanned;
    }

    private MultiValueMap<String, String> form(String token, String ownerId, int count) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("access_token", token == null ? "" : token);
        form.add("v", apiVersion);
        form.add("owner_id", ownerId == null ? "" : ownerId);
        form.add("count", String.valueOf(count));
        form.add("need_likes", "0");
        form.add("preview_length", "0");
        return form;
    }

    private JsonNode call(String method, MultiValueMap<String, String> form) {
        JsonNode body = restClient
                .post()
                .uri("/method/" + method)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        if (body == null || body.has("error")) {
            String msg = body == null
                    ? "empty response"
                    : body.path("error").path("error_msg").asText("vk error");
            throw new IllegalStateException("VK " + method + " failed: " + msg);
        }
        return body;
    }

    /** Visible for tests that stub a single page without Spring. */
    public static List<String> commentIds(JsonNode items) {
        List<String> ids = new ArrayList<>();
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                String id = item.path("id").asText(null);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }
}
