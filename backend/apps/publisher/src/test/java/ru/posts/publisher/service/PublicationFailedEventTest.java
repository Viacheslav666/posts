package ru.posts.publisher.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.posts.social.vk.VkWallPostGate;

class PublicationFailedEventTest {

    @Test
    void payloadIncludesReasonAndPostLinkFields() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        String json = PublicationFailedEvent.payload(
                new ObjectMapper(),
                "vk",
                "no_permissions",
                workspaceId,
                postId,
                channelId,
                publicationId,
                VkWallPostGate.BLOCK_MESSAGE);

        var node = new ObjectMapper().readTree(json);
        assertThat(node.path("network").asText()).isEqualTo("vk");
        assertThat(node.path("result").asText()).isEqualTo("no_permissions");
        assertThat(node.path("workspaceId").asText()).isEqualTo(workspaceId.toString());
        assertThat(node.path("postId").asText()).isEqualTo(postId.toString());
        assertThat(node.path("channelId").asText()).isEqualTo(channelId.toString());
        assertThat(node.path("publicationId").asText()).isEqualTo(publicationId.toString());
        assertThat(node.path("errorCode").asText()).isEqualTo("no_permissions");
        assertThat(node.path("errorMessage").asText()).isEqualTo(VkWallPostGate.BLOCK_MESSAGE);
        assertThat(node.path("errorMessage").asText()).doesNotContain("—");
    }

    @Test
    void russianMessageForVkProfileDenial() {
        assertThat(PublicationFailedEvent.russianMessage("vk", "no_permissions", "Method is not available"))
                .isEqualTo(VkWallPostGate.BLOCK_MESSAGE);
        assertThat(PublicationFailedEvent.russianMessage("vk", "auth", "User authorization failed"))
                .isEqualTo("Канал нужно переподключить: доступ отклонён.");
        assertThat(PublicationFailedEvent.russianMessage("tg", "attempts_exhausted", "timeout"))
                .isEqualTo("Не удалось опубликовать после нескольких попыток.");
    }
}
