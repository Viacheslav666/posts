package ru.posts.grabber.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GrabbedAttachmentsTest {

    @ParameterizedTest
    @CsvSource({
        "photo,photo",
        "video,video",
        "clip,clip",
        "audio,audio",
        "audio_playlist,audio_playlist",
        "doc,doc",
        "poll,poll",
        "album,album",
        "market,market",
        "market_album,market_album",
        "page,wiki",
        "link,link",
        "article,article",
        "sticker,other"
    })
    void kindOfVkTypes(String type, String kind) {
        assertThat(GrabbedAttachments.kindOf(Map.of("type", type))).isEqualTo(kind);
    }

    @Test
    void shortVideoIsClipNotVideo() {
        assertThat(GrabbedAttachments.kindOf(Map.of("type", "video", "is_clip", true)))
                .isEqualTo(GrabbedAttachments.CLIP);
        assertThat(GrabbedAttachments.kindOf(Map.of("type", "video", "video_type", "short_video")))
                .isEqualTo(GrabbedAttachments.CLIP);
    }

    @Test
    void referencesIncludeAccessKey() {
        assertThat(GrabbedAttachments.reference(
                        Map.of("type", "photo", "owner_id", "-1", "item_id", "2", "access_key", "abc")))
                .isEqualTo("photo-1_2_abc");
        assertThat(GrabbedAttachments.reference(
                        Map.of("type", "video", "is_clip", true, "owner_id", "-1", "item_id", "3")))
                .isEqualTo("video-1_3");
        assertThat(GrabbedAttachments.reference(Map.of("type", "page", "owner_id", "-5", "item_id", "9")))
                .isEqualTo("page-5_9");
        assertThat(GrabbedAttachments.reference(Map.of("type", "audio_playlist", "owner_id", "1", "item_id", "4")))
                .isEqualTo("audio_playlist1_4");
        assertThat(GrabbedAttachments.reference(Map.of("type", "market_album", "owner_id", "-7", "item_id", "1")))
                .isEqualTo("market_album-7_1");
    }

    @Test
    void linksAndArticlesUseUrl() {
        assertThat(GrabbedAttachments.reference(Map.of("type", "link", "url", "https://example.com/a")))
                .isEqualTo("https://example.com/a");
        assertThat(GrabbedAttachments.reference(Map.of("type", "article", "url", "https://vk.com/@club-x")))
                .isEqualTo("https://vk.com/@club-x");
        assertThat(GrabbedAttachments.reference(Map.of("type", "link", "url", "javascript:alert(1)")))
                .isNull();
    }

    @Test
    void legacyItemsFallBackToCanonicalId() {
        assertThat(GrabbedAttachments.reference(Map.of("type", "photo", "id", "photo-1_2")))
                .isEqualTo("photo-1_2");
        assertThat(GrabbedAttachments.reference(Map.of("type", "poll"))).isNull();
    }
}
