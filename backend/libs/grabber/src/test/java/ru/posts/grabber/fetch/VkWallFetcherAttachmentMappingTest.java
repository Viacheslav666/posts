package ru.posts.grabber.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.posts.grabber.media.GrabbedAttachments;

class VkWallFetcherAttachmentMappingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Object> map(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        return VkWallFetcher.mapAttachment(node);
    }

    @Test
    void photoPicksLargestSizeAndKeepsAccessKey() throws Exception {
        Map<String, Object> att = map(
                """
                {"type":"photo","photo":{"id":5,"owner_id":-10,"access_key":"k1","sizes":[
                  {"type":"s","url":"https://sun.userapi.com/s.jpg","width":75,"height":50},
                  {"type":"w","url":"https://sun.userapi.com/w.jpg","width":2560,"height":1440},
                  {"type":"m","url":"https://sun.userapi.com/m.jpg","width":130,"height":87}
                ]}}
                """);
        assertThat(att)
                .containsEntry("id", "photo-10_5")
                .containsEntry("owner_id", "-10")
                .containsEntry("item_id", "5")
                .containsEntry("access_key", "k1")
                .containsEntry("url", "https://sun.userapi.com/w.jpg")
                .containsEntry("kind", "photo");
        assertThat(GrabbedAttachments.reference(att)).isEqualTo("photo-10_5_k1");
    }

    @Test
    void shortVideoIsClipAndPrivateVideoFlagged() throws Exception {
        Map<String, Object> clip = map(
                """
                {"type":"video","video":{"id":1,"owner_id":-10,"type":"short_video","title":"Clip"}}
                """);
        assertThat(clip).containsEntry("kind", "clip").containsEntry("is_clip", true);

        Map<String, Object> priv = map(
                """
                {"type":"video","video":{"id":2,"owner_id":-10,"is_private":1,"title":"Secret","duration":12}}
                """);
        assertThat(priv)
                .containsEntry("kind", "video")
                .containsEntry("is_private", true)
                .containsEntry("title", "Secret")
                .containsEntry("duration", 12);
    }

    @Test
    void wikiPageUsesGroupIdAsOwner() throws Exception {
        Map<String, Object> page = map(
                """
                {"type":"page","page":{"id":77,"group_id":42,"title":"Правила"}}
                """);
        assertThat(page).containsEntry("kind", "wiki").containsEntry("owner_id", "-42");
        assertThat(GrabbedAttachments.reference(page)).isEqualTo("page-42_77");
    }

    @Test
    void linkArticleDocAndMarket() throws Exception {
        assertThat(map("{\"type\":\"link\",\"link\":{\"url\":\"https://example.com\",\"title\":\"Ex\"}}"))
                .containsEntry("kind", "link")
                .containsEntry("url", "https://example.com");
        assertThat(map("{\"type\":\"article\",\"article\":{\"id\":3,\"owner_id\":-1,\"url\":\"https://vk.com/@a\"}}"))
                .containsEntry("kind", "article")
                .containsEntry("url", "https://vk.com/@a");
        Map<String, Object> doc =
                map("{\"type\":\"doc\",\"doc\":{\"id\":4,\"owner_id\":1,\"access_key\":\"z\",\"size\":100}}");
        assertThat(GrabbedAttachments.reference(doc)).isEqualTo("doc1_4_z");
        assertThat(map("{\"type\":\"market\",\"market\":{\"id\":9,\"owner_id\":-3}}"))
                .containsEntry("kind", "market");
        assertThat(map("{\"type\":\"audio_playlist\",\"audio_playlist\":{\"id\":9,\"owner_id\":-3}}"))
                .containsEntry("kind", "audio_playlist");
    }
}
