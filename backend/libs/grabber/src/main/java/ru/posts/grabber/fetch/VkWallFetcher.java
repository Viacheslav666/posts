package ru.posts.grabber.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import ru.posts.grabber.media.GrabbedAttachments;

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
                Map<String, Object> mapped = mapAttachment(att);
                Object cid = mapped.get("id");
                if (cid != null) {
                    canonical.add(cid.toString());
                }
                attachments.add(mapped);
            }
        }
        Instant publishedAt =
                item.has("date") ? Instant.ofEpochSecond(item.path("date").asLong()) : null;
        boolean signed = item.has("signer_id") && item.path("signer_id").asLong(0) != 0;
        // wall.get filter=owner → group/page posts; from_id equals owner_id for community posts
        long fromId = item.path("from_id").asLong(ownerId);
        boolean fromGroup = fromId == ownerId || fromId < 0;
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
                publishedAt,
                signed,
                fromGroup,
                raw);
    }

    /**
     * Normalizes one {@code wall.get} attachment into the map stored in {@code
     * grabber_items.attachments}: {@code type}, precise {@code kind}, canonical {@code id}, plus the
     * fields needed to re-reference or re-upload it ({@code owner_id}, {@code item_id}, {@code
     * access_key}, {@code url}, {@code title}, video privacy).
     */
    static Map<String, Object> mapAttachment(JsonNode att) {
        String type = att.path("type").asText("link");
        JsonNode payload = att.path(type);
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("type", type);

        String owner = null;
        if (payload.hasNonNull("owner_id")) {
            owner = payload.path("owner_id").asText();
        } else if ("page".equals(type) && payload.hasNonNull("group_id")) {
            long groupId = Math.abs(payload.path("group_id").asLong());
            owner = groupId == 0 ? null : "-" + groupId;
        }
        if (owner != null && payload.hasNonNull("id")) {
            String itemId = payload.path("id").asText();
            mapped.put("id", type + owner + "_" + itemId);
            mapped.put("owner_id", owner);
            mapped.put("item_id", itemId);
        }
        String accessKey = payload.path("access_key").asText("");
        if (!accessKey.isBlank()) {
            mapped.put("access_key", accessKey);
        }
        String title = payload.path("title").asText("");
        if (!title.isBlank()) {
            mapped.put("title", title);
        }

        switch (type) {
            case "photo" -> {
                String best = bestPhotoUrl(payload);
                if (best != null) {
                    mapped.put("url", best);
                }
            }
            case "video", "clip", "short_video" -> {
                String videoType = payload.path("type").asText("");
                boolean clip = !"video".equals(type)
                        || videoType.toLowerCase(Locale.ROOT).contains("short")
                        || payload.path("is_short_video").asBoolean(false);
                if (clip) {
                    mapped.put("is_clip", true);
                    mapped.put("video_type", "short_video");
                }
                if (payload.path("is_private").asInt(0) == 1) {
                    mapped.put("is_private", true);
                }
                if (payload.hasNonNull("platform")) {
                    mapped.put("platform", payload.path("platform").asText());
                }
                if (payload.hasNonNull("duration")) {
                    mapped.put("duration", payload.path("duration").asInt());
                }
                String description = payload.path("description").asText("");
                if (!description.isBlank()) {
                    mapped.put("description", description);
                }
            }
            case "doc" -> {
                if (payload.hasNonNull("url")) {
                    mapped.put("url", payload.path("url").asText());
                }
                if (payload.hasNonNull("size")) {
                    mapped.put("size", payload.path("size").asLong());
                }
            }
            case "link", "article" -> {
                String url = payload.path("url").asText("");
                if (url.isBlank()) {
                    url = payload.path("view_url").asText("");
                }
                if (!url.isBlank()) {
                    mapped.put("url", url);
                }
            }
            default -> {
                if (payload.hasNonNull("url")) {
                    mapped.put("url", payload.path("url").asText());
                }
            }
        }
        mapped.put("kind", GrabbedAttachments.kindOf(mapped));
        return mapped;
    }

    private static String bestPhotoUrl(JsonNode photo) {
        String origUrl = photo.path("orig_photo").path("url").asText("");
        if (!origUrl.isBlank()) {
            return origUrl;
        }
        String best = null;
        long bestArea = -1;
        for (JsonNode size : photo.path("sizes")) {
            String url = size.path("url").asText(size.path("src").asText(""));
            if (url.isBlank()) {
                continue;
            }
            long area = size.path("width").asLong(0) * size.path("height").asLong(0);
            if (area > bestArea) {
                bestArea = area;
                best = url;
            }
        }
        if (best == null && photo.hasNonNull("url")) {
            best = photo.path("url").asText();
        }
        return best;
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
