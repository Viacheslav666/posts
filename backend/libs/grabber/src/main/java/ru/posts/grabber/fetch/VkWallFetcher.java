package ru.posts.grabber.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    /**
     * Numeric ids are community owner ids (a bare positive id is negated). Screen names and
     * {@code vk.com/team} go out as {@code domain} with no {@code owner_id}.
     */
    public static WallLookup lookup(String sourceId) {
        String value = sourceId == null ? "" : sourceId.trim();
        if (value.matches("-?\\d+")) {
            if (!value.startsWith("-")) {
                value = "-" + value;
            }
            return new WallLookup("owner_id", value);
        }
        return new WallLookup("domain", value);
    }

    public record WallLookup(String param, String value) {}

    public List<SourceCandidate> fetch(String sourceId, String accessToken, int count) {
        WallLookup lookup = lookup(sourceId);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add(lookup.param(), lookup.value());
        form.add("count", String.valueOf(Math.max(1, Math.min(count, 50))));
        form.add("filter", "owner");
        form.add("extended", "1");
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
        JsonNode response = body.path("response");
        JsonNode items = response.path("items");
        Map<Long, String> profileNames = profileNames(response.path("profiles"));
        List<SourceCandidate> out = new ArrayList<>();
        if (!items.isArray()) {
            return out;
        }
        for (JsonNode item : items) {
            out.add(mapItem(sourceId, item, profileNames));
        }
        return out;
    }

    private static Map<Long, String> profileNames(JsonNode profiles) {
        Map<Long, String> names = new HashMap<>();
        if (profiles == null || !profiles.isArray()) {
            return names;
        }
        for (JsonNode profile : profiles) {
            long id = profile.path("id").asLong(0);
            String name = (profile.path("first_name").asText("") + " "
                            + profile.path("last_name").asText(""))
                    .trim();
            if (id != 0 && !name.isEmpty()) {
                names.put(id, name);
            }
        }
        return names;
    }

    private SourceCandidate mapItem(String sourceId, JsonNode item, Map<Long, String> profileNames) {
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
                if ("video".equals(type)) {
                    String videoType = payload.path("type").asText("");
                    if (videoType.toLowerCase(Locale.ROOT).contains("short")
                            || payload.path("is_short_video").asBoolean(false)) {
                        mapped.put("is_clip", true);
                        mapped.put("video_type", "short_video");
                    }
                }
                attachments.add(mapped);
            }
        }
        Instant publishedAt =
                item.has("date") ? Instant.ofEpochSecond(item.path("date").asLong()) : null;
        boolean signed = item.has("signer_id") && item.path("signer_id").asLong(0) != 0;
        String signerName = signed ? profileNames.get(item.path("signer_id").asLong()) : null;
        // wall.get filter=owner → group/page posts; from_id equals owner_id for community posts
        long fromId = item.path("from_id").asLong(ownerId);
        boolean fromGroup = fromId == ownerId || fromId < 0;
        Map<String, Object> raw = new LinkedHashMap<>();
        Map<String, Object> converted = objectMapper.convertValue(item, Map.class);
        if (converted != null) {
            raw.putAll(converted);
        }
        raw.put("source_text", text);
        raw.put("signed", signed);
        if (signerName != null && !signerName.isBlank()) {
            raw.put("signer_name", signerName);
        }
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
                publishedAt,
                signed,
                fromGroup,
                raw);
    }
}
