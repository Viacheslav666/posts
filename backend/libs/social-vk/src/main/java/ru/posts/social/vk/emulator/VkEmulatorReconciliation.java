package ru.posts.social.vk.emulator;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.springframework.web.client.RestClient;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.PublishResult;

/**
 * Emulator-only reconciliation via {@code GET /_control/posts}. Must not be referenced from
 * production (API) reconciliation paths — ArchUnit bans {@code /_control} outside this package.
 */
public final class VkEmulatorReconciliation {

    private VkEmulatorReconciliation() {}

    public static Optional<PublishResult> find(
            RestClient restClient, ChannelContext ctx, String idempotencyKey, boolean story) {
        try {
            JsonNode posts = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/_control/posts")
                            .queryParam("network", "vk")
                            .queryParam("channelId", ctx.externalId())
                            .queryParam("contentType", story ? "story" : "post")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (posts == null || !posts.isArray()) {
                return Optional.empty();
            }
            for (JsonNode post : posts) {
                if (idempotencyKey != null && idempotencyKey.equals(textOrNull(post, "idempotencyKey"))) {
                    String remoteId = textOrNull(post, "remotePostId");
                    if (remoteId != null) {
                        String url = story
                                ? "https://vk.com/story" + ctx.externalId() + "_" + remoteId
                                : "https://vk.com/wall" + ctx.externalId() + "_" + remoteId;
                        return Optional.of(new PublishResult(remoteId, url));
                    }
                }
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
