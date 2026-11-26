package ru.posts.publisher.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import ru.posts.social.vk.VkWallPostGate;

/** Payload and Russian copy for {@code publication.failed}. */
public final class PublicationFailedEvent {

    private PublicationFailedEvent() {}

    public static String russianMessage(String network, String errorCode, String raw) {
        if (raw != null && raw.contains("VK не разрешает публикацию")) {
            return VkWallPostGate.BLOCK_MESSAGE;
        }
        if ("no_permissions".equals(errorCode) || VkWallPostGate.PROFILE_TYPE_ERROR.equals(errorCode)) {
            if ("vk".equals(network)) {
                return VkWallPostGate.BLOCK_MESSAGE;
            }
            return "Недостаточно прав для публикации. Переподключите канал.";
        }
        return switch (errorCode == null ? "" : errorCode) {
            case "auth" -> "Канал нужно переподключить: доступ отклонён.";
            case "attempts_exhausted" -> "Не удалось опубликовать после нескольких попыток.";
            case "permanent" -> "Публикация отклонена и не будет повторена.";
            default -> "Не удалось опубликовать пост.";
        };
    }

    public static String payload(
            ObjectMapper objectMapper,
            String network,
            String errorCode,
            UUID workspaceId,
            UUID postId,
            UUID channelId,
            UUID publicationId,
            String errorMessage) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("network", network == null ? "" : network);
        body.put("result", errorCode == null ? "" : errorCode);
        body.put("workspaceId", workspaceId == null ? "" : workspaceId.toString());
        body.put("postId", postId == null ? "" : postId.toString());
        body.put("channelId", channelId == null ? "" : channelId.toString());
        body.put("publicationId", publicationId == null ? "" : publicationId.toString());
        body.put("errorCode", errorCode == null ? "" : errorCode);
        body.put("errorMessage", errorMessage == null ? "" : errorMessage);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
