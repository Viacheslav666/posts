package ru.posts.api.post;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.post.PostDtos;

/** Maps PostContent / options / recurrence between API DTOs and JSONB maps. */
public final class PostContentMapper {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private PostContentMapper() {}

    public static Map<String, Object> contentToMap(PostDtos.PostContent content, ObjectMapper mapper) {
        if (content == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        if (content.text() != null) {
            map.put("text", content.text());
        }
        if (content.doc() != null) {
            map.put("doc", content.doc());
        }
        if (content.mediaIds() != null) {
            map.put("mediaIds", content.mediaIds().stream().map(UUID::toString).toList());
        }
        if (content.stringAttachments() != null && !content.stringAttachments().isEmpty()) {
            map.put("stringAttachments", List.copyOf(content.stringAttachments()));
        }
        return map;
    }

    public static PostDtos.PostContent mapToContent(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return new PostDtos.PostContent("", null, List.of(), null);
        }
        String text = map.get("text") == null ? "" : String.valueOf(map.get("text"));
        @SuppressWarnings("unchecked")
        Map<String, Object> doc = map.get("doc") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
        List<UUID> mediaIds = new ArrayList<>();
        Object rawIds = map.get("mediaIds");
        if (rawIds instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    mediaIds.add(UUID.fromString(String.valueOf(item)));
                }
            }
        }
        List<String> stringAttachments = new ArrayList<>();
        Object rawAttachments = map.get("stringAttachments");
        if (rawAttachments instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    String value = String.valueOf(item).trim();
                    if (!value.isEmpty()) {
                        stringAttachments.add(value);
                    }
                }
            }
        }
        return new PostDtos.PostContent(
                text, doc, List.copyOf(mediaIds), stringAttachments.isEmpty() ? null : List.copyOf(stringAttachments));
    }

    public static List<String> stringAttachments(PostDtos.PostContent content) {
        return content == null || content.stringAttachments() == null ? List.of() : content.stringAttachments();
    }

    public static Map<String, Object> optionsToMap(PostDtos.PostVariantOptions options, ObjectMapper mapper) {
        if (options == null) {
            return Map.of();
        }
        return mapper.convertValue(options, MAP);
    }

    public static PostDtos.PostVariantOptions mapToOptions(Map<String, Object> map, ObjectMapper mapper) {
        if (map == null || map.isEmpty()) {
            return new PostDtos.PostVariantOptions(
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null);
        }
        return mapper.convertValue(map, PostDtos.PostVariantOptions.class);
    }

    public static Map<String, Object> recurrenceToMap(PostDtos.Recurrence recurrence, ObjectMapper mapper) {
        if (recurrence == null) {
            return null;
        }
        return mapper.convertValue(recurrence, MAP);
    }

    public static PostDtos.Recurrence mapToRecurrence(Map<String, Object> map, ObjectMapper mapper) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return mapper.convertValue(map, PostDtos.Recurrence.class);
    }

    public static Map<String, Object> repostToMap(PostDtos.RepostTarget repost, ObjectMapper mapper) {
        if (repost == null) {
            return null;
        }
        Map<String, Object> map = mapper.convertValue(repost, MAP);
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("repost", map);
        return wrapped;
    }

    public static PostDtos.RepostTarget extractRepost(Map<String, Object> baseContent, ObjectMapper mapper) {
        if (baseContent == null) {
            return null;
        }
        Object raw = baseContent.get("repost");
        if (!(raw instanceof Map<?, ?>)) {
            return null;
        }
        return mapper.convertValue(raw, PostDtos.RepostTarget.class);
    }

    public static String extractText(PostDtos.PostContent content) {
        if (content == null) {
            return "";
        }
        if (content.text() != null && !content.text().isBlank()) {
            return content.text();
        }
        return "";
    }

    public static List<UUID> mediaIds(PostDtos.PostContent content) {
        return content == null || content.mediaIds() == null ? List.of() : content.mediaIds();
    }

    public static String autoTitle(PostDtos.PostContent content) {
        String text = extractText(content)
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.isEmpty()) {
            return "Без названия";
        }
        return text.length() <= 80 ? text : text.substring(0, 80);
    }

    public static ApiEnums.Network parseNetwork(String network) {
        return ApiEnums.Network.valueOf(network);
    }
}
