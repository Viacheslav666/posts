package ru.posts.social.inbound.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.posts.social.inbound.SocialEventKind;
import ru.posts.social.inbound.SocialInboundEvent;

class NormalizersTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void vkSuggestMapsToPostSuggested() throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("type", "wall_post_new");
        body.put("event_id", "ev-1");
        body.put("group_id", "123");
        ObjectNode object = body.putObject("object");
        object.put("id", 10);
        object.put("from_id", 55);
        object.put("post_type", "suggest");
        object.put("text", "please post");
        object.put("date", 1_700_000_000L);

        SocialInboundEvent event = VkEventNormalizer.normalize(
                body, UUID.randomUUID(), UUID.randomUUID(), List.of("suggest"), Instant.now());
        assertThat(event.kind()).isEqualTo(SocialEventKind.POST_SUGGESTED);
        assertThat(event.dedupKey()).isEqualTo("ev-1");
        assertThat(event.text()).isEqualTo("please post");
        assertThat(event.raw()).doesNotContain("secret");
    }

    @Test
    void telegramAlbumMergeKeepsAllAttachments() throws Exception {
        UUID botId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID ws = UUID.randomUUID();
        var u1 = mapper.readTree(
                """
                {"update_id":1,"channel_post":{"message_id":1,"date":1,"media_group_id":"g1","chat":{"id":-1,"type":"channel"},"photo":[{"file_id":"a","file_size":1}]}}
                """);
        var u2 = mapper.readTree(
                """
                {"update_id":2,"channel_post":{"message_id":2,"date":1,"media_group_id":"g1","chat":{"id":-1,"type":"channel"},"photo":[{"file_id":"b","file_size":1}]}}
                """);
        var u3 = mapper.readTree(
                """
                {"update_id":3,"channel_post":{"message_id":3,"date":1,"media_group_id":"g1","chat":{"id":-1,"type":"channel"},"photo":[{"file_id":"c","file_size":1}]}}
                """);
        SocialInboundEvent event = TelegramEventNormalizer.mergeAlbum(
                List.of(u1, u2, u3), botId, channelId, ws, List.of("antispam"), Instant.now());
        assertThat(event.kind()).isEqualTo(SocialEventKind.POST_NEW);
        assertThat(event.attachments()).hasSize(3);
        assertThat(event.dedupKey()).contains("album:g1");
    }

    @Test
    void maxCommentCreatedNormalized() throws Exception {
        var body = mapper.readTree(
                """
                {"update_type":"comment_created","timestamp":1700000000000,"comment":{"id":"c9","text":"hi","sender":{"user_id":"u1"}}}
                """);
        SocialInboundEvent event = MaxEventNormalizer.normalize(
                body, UUID.randomUUID(), UUID.randomUUID(), List.of("antispam"), Instant.now());
        assertThat(event.kind()).isEqualTo(SocialEventKind.COMMENT_NEW);
        assertThat(event.text()).isEqualTo("hi");
        assertThat(event.dedupKey()).hasSize(64);
    }
}
