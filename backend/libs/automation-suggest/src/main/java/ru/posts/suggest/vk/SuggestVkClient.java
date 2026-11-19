package ru.posts.suggest.vk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Thin VK wall.get client for suggest sync (filter=suggests / getById). */
@Profile("!openapi")
@Component
public class SuggestVkClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiVersion;

    public SuggestVkClient(
            ObjectMapper objectMapper,
            @Value("${posts.social.vk.base-url:${posts.social-inbound.social-base-url:https://api.vk.com}}")
                    String baseUrl,
            @Value("${posts.social.vk.api-version:5.199}") String apiVersion) {
        this.objectMapper = objectMapper;
        this.apiVersion = apiVersion;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(20));
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restClient =
                RestClient.builder().baseUrl(normalized).requestFactory(factory).build();
    }

    public List<SuggestWallPost> getSuggests(String ownerId, String accessToken, int count) {
        MultiValueMap<String, String> form = baseForm(accessToken);
        form.add("owner_id", ownerId);
        form.add("filter", "suggests");
        form.add("count", String.valueOf(Math.max(1, Math.min(count, 100))));
        JsonNode body = call("wall.get", form);
        return parseItems(body.path("response").path("items"));
    }

    public SuggestWallPost getById(String ownerId, String postId, String accessToken) {
        MultiValueMap<String, String> form = baseForm(accessToken);
        form.add("posts", ownerId + "_" + postId);
        JsonNode body = call("wall.getById", form);
        JsonNode items = body.path("response");
        if (items.isObject()) {
            items = items.path("items");
        }
        List<SuggestWallPost> parsed = parseItems(items);
        return parsed.isEmpty() ? null : parsed.getFirst();
    }

    public boolean isMessagesAllowed(String groupId, String userId, String accessToken) {
        MultiValueMap<String, String> form = baseForm(accessToken);
        form.add("group_id", stripMinus(groupId));
        form.add("user_id", userId);
        JsonNode body = call("messages.isMessagesFromGroupAllowed", form);
        JsonNode response = body.path("response");
        return response.path("is_allowed").asInt(0) == 1 || response.asBoolean(false) || response.asInt(0) == 1;
    }

    public boolean isClosedProfile(String userId, String accessToken) {
        MultiValueMap<String, String> form = baseForm(accessToken);
        form.add("user_ids", userId);
        form.add("fields", "is_closed");
        JsonNode body = call("users.get", form);
        JsonNode items = body.path("response");
        if (items.isArray() && !items.isEmpty()) {
            return items.get(0).path("is_closed").asBoolean(false)
                    || items.get(0).path("is_closed").asInt(0) == 1;
        }
        return false;
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

    private MultiValueMap<String, String> baseForm(String accessToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("access_token", accessToken == null ? "" : accessToken);
        form.add("v", apiVersion);
        return form;
    }

    private List<SuggestWallPost> parseItems(JsonNode items) {
        List<SuggestWallPost> out = new ArrayList<>();
        if (items == null || !items.isArray()) {
            return out;
        }
        for (JsonNode item : items) {
            out.add(mapItem(item));
        }
        return out;
    }

    private SuggestWallPost mapItem(JsonNode item) {
        String id = item.path("id").asText(null);
        String fromId = item.path("from_id").asText(null);
        String text = item.path("text").asText("");
        boolean markedAsAds = item.path("marked_as_ads").asInt(0) == 1
                || item.path("marked_as_ads").asBoolean(false);
        boolean hasErid =
                text != null && text.toLowerCase(java.util.Locale.ROOT).contains("erid");
        String postType = item.path("post_type").asText("");
        List<Map<String, Object>> attachments = new ArrayList<>();
        JsonNode atts = item.path("attachments");
        if (atts.isArray()) {
            for (JsonNode a : atts) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("type", a.path("type").asText("unknown"));
                String type = a.path("type").asText("unknown");
                JsonNode payload = a.path(type);
                if (!payload.isMissingNode()) {
                    map.put(
                            "remoteRef",
                            payload.path("owner_id").asText("") + "_"
                                    + payload.path("id").asText(""));
                }
                attachments.add(map);
            }
        }
        Map<String, Object> raw;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> converted = objectMapper.convertValue(item, Map.class);
            raw = converted;
        } catch (Exception ex) {
            raw = Map.of();
        }
        return new SuggestWallPost(id, fromId, text, attachments, markedAsAds, hasErid, postType, raw);
    }

    private static String stripMinus(String id) {
        if (id == null) {
            return "";
        }
        return id.startsWith("-") ? id.substring(1) : id;
    }

    public record SuggestWallPost(
            String id,
            String fromId,
            String text,
            List<Map<String, Object>> attachments,
            boolean markedAsAds,
            boolean hasErid,
            String postType,
            Map<String, Object> raw) {}
}
