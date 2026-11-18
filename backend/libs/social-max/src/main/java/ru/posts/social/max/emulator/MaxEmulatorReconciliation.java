package ru.posts.social.max.emulator;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.springframework.web.client.RestClient;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.PublishResult;

/** Emulator-only reconciliation via {@code GET /_control/posts} (BE-23 ArchUnit). */
public final class MaxEmulatorReconciliation {

    private MaxEmulatorReconciliation() {}

    public static Optional<PublishResult> find(RestClient restClient, ChannelContext ctx, String idempotencyKey) {
        try {
            JsonNode posts = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/_control/posts")
                            .queryParam("network", "max")
                            .queryParam("channelId", ctx.externalId())
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
                        return Optional.of(
                                new PublishResult(remoteId, "https://max.ru/c/" + ctx.externalId() + "/" + remoteId));
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
