package ru.posts.grabber.fetch;

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

/** Reads VK wall posts for grabber sources (wall.get). */
@Component
@Profile("!openapi")
public class VkWallFetcher {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiVersion;

    public VkWallFetcher(
            ObjectMapper objectMapper,
            @Value("${posts.grabber.vk.base-url:https://api.vk.com}") String baseUrl,
            @Value("${posts.grabber.vk.api-version:5.199}") String apiVersion) {
        this.objectMapper = objectMapper;
        this.apiVersion = apiVersion;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(15));
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restClient =
                RestClient.builder().baseUrl(normalized).requestFactory(factory).build();
    }

    public List<SourceCandidate> fetch(String sourceId, String accessToken, int count) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("owner_id", toOwnerId(sourceId));
        form.add("count", String.valueOf(Math.max(1, Math.min(count, 50))));
        form.add("filter", "owner");
        form.add("access_token", accessToken);
        form.add("v", apiVersion);
        JsonNode body = restClient
                .post()
                .uri("/method/wall.get")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        if (body == null || body.has("error")) {
            String msg = body == null
                    ? "empty response"
                    : body.path("error").path("error_msg").asText("vk error");
            throw new IllegalStateException("VK wall.get failed: " + msg);
        }
        JsonNode items = body.path("response").path("items");
        List<SourceCandidate> out = new ArrayList<>();
        if (!items.isArray()) {
            return out;
        }
        for (JsonNode item : items) {
            out.add(mapItem(sourceId, item));
        }
        return out;
    }

    private SourceCandidate mapItem(String sourceId, JsonNode item) {
        long ownerId = item.path("owner_id").asLong();
        long postId = item.path("id").asLong();
        String remotePostId = "wall" + ownerId + "_" + postId;
        String permalink = "https://vk.com/" + remotePostId;
        String text = item.path("text").asText("");
        boolean ads = item.path("marked_as_ads").asInt(0) == 1;
        boolean repost = item.has("copy_history")
                && item.path("copy_history").isArray()
                && !item.path("copy_history").isEmpty();
        List<Map<String, Object>> attachments = new ArrayList<>();
        List<String> canonical = new ArrayList<>();
        JsonNode atts = item.path("attachments");
        if (atts.isArray()) {
            for (JsonNode att : atts) {
                String type = att.path("type").asText("link");
                Map<String, Object> mapped = new LinkedHashMap<>();
                mapped.put("type", type);
                JsonNode payload = att.path(type);
                if (payload.has("id") && payload.has("owner_id")) {
                    String cid = type + payload.path("owner_id").asText() + "_"
                            + payload.path("id").asText();
                    mapped.put("id", cid);
                    canonical.add(cid);
                }
                if (payload.has("url")) {
                    mapped.put("url", payload.path("url").asText());
                }
                attachments.add(mapped);
            }
        }
        Map<String, Object> raw = objectMapper.convertValue(item, Map.class);
        return new SourceCandidate(
                "vk",
                sourceId,
                remotePostId,
                permalink,
                sourceId,
                text,
                null,
                attachments,
                List.of(),
                canonical,
                ads,
                repost,
                raw);
    }

    private static String toOwnerId(String sourceId) {
        if (sourceId.startsWith("-") || sourceId.chars().allMatch(Character::isDigit)) {
            return sourceId;
        }
        // screen name unresolved — pass as domain via wall.get owner_id requires numeric;
        // callers should resolve; keep negative numeric when possible.
        return sourceId;
    }
}
