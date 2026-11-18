package ru.posts.social.ok.emulator;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.springframework.web.client.RestClient;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.PublishResult;

/**
 * Emulator-only reconciliation via {@code GET /_control/posts}. ArchUnit bans {@code /_control}
 * outside this package (BE-23 / ADR-0012).
 */
public final class OkEmulatorReconciliation {

    private OkEmulatorReconciliation() {}

    public static Optional<PublishResult> find(
            RestClient restClient, ChannelContext ctx, String idempotencyKey, boolean story) {
        try {
            JsonNode posts = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/_control/posts")
                            .queryParam("network", "ok")
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
                                ? "https://ok.ru/group/" + ctx.externalId() + "/moment/" + remoteId
                                : "https://ok.ru/group/" + ctx.externalId() + "/topic/" + remoteId;
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
