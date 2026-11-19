package ru.posts.crosspost.transform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.posts.crosspost.config.CrosspostConfigSupport;
import ru.posts.crosspost.model.CrosspostCandidate;

class CrosspostTransformerTest {

    @Test
    void removeLinksAndHashtags() {
        Map<String, Object> config = CrosspostConfigSupport.withDefaults(Map.of(
                "transform", Map.of("remove_links", true, "remove_hashtags", true)));
        var result = CrosspostTransformer.transform(
                candidate("см https://example.com и #новость"), config, Map.of(), "vk", null);
        assertThat(result.text()).doesNotContain("https://").doesNotContain("#");
        assertThat(result.notes()).contains("links_removed", "hashtags_removed");
    }

    @Test
    void addSourceLink() {
        Map<String, Object> config =
                CrosspostConfigSupport.withDefaults(Map.of("transform", Map.of("add_source_link", true)));
        var result = CrosspostTransformer.transform(
                candidate("текст"), config, Map.of(), "vk", "https://vk.com/wall-1_2");
        assertThat(result.text()).contains("https://vk.com/wall-1_2");
    }

    @Test
    void closeCommentsForVkOk() {
        Map<String, Object> config =
                CrosspostConfigSupport.withDefaults(Map.of("transform", Map.of("close_comments", true)));
        var vk = CrosspostTransformer.transform(candidate("t"), config, Map.of(), "vk", null);
        var tg = CrosspostTransformer.transform(candidate("t"), config, Map.of(), "tg", null);
        assertThat(vk.options()).containsEntry("closeComments", true);
        assertThat(tg.options()).doesNotContainKey("closeComments");
    }

    @Test
    void tgSplitCaptionOverflow() {
        String longText = "x".repeat(1500);
        Map<String, Object> config = CrosspostConfigSupport.withDefaults(Map.of());
        CrosspostCandidate c = CrosspostCandidate.builder()
                .network("tg")
                .remoteId("1")
                .text(longText)
                .attachments(List.of(Map.of("type", "photo", "url", "http://x/a.jpg")))
                .hasImage(true)
                .build();
        var result = CrosspostTransformer.transform(c, config, Map.of(), "tg", null);
        assertThat(result.text()).hasSize(CrosspostTransformer.TG_CAPTION_MAX);
        assertThat(result.overflowText()).isNotBlank();
        assertThat(result.notes()).contains("tg_split_caption_overflow");
    }

    @Test
    void tgTruncateReadMore() {
        String longText = "y".repeat(2000);
        Map<String, Object> config = CrosspostConfigSupport.withDefaults(
                Map.of("transform", Map.of("long_text_mode", "truncate_read_more")));
        CrosspostCandidate c = CrosspostCandidate.builder()
                .network("tg")
                .remoteId("1")
                .text(longText)
                .attachments(List.of(Map.of("type", "photo")))
                .hasImage(true)
                .build();
        var result = CrosspostTransformer.transform(c, config, Map.of(), "tg", "https://t.me/c/1");
        assertThat(result.text()).contains("Читать полностью");
        assertThat(result.overflowText()).isNull();
    }

    @Test
    void vkVideoAsLink() {
        Map<String, Object> config = CrosspostConfigSupport.withDefaults(Map.of());
        CrosspostCandidate c = CrosspostCandidate.builder()
                .network("vk")
                .remoteId("1")
                .text("post")
                .attachments(List.of(Map.of("type", "video", "url", "https://vk.com/video1")))
                .hasVideo(true)
                .build();
        var result = CrosspostTransformer.transform(c, config, Map.of(), "tg", null);
        assertThat(result.attachments()).isEmpty();
        assertThat(result.text()).contains("https://vk.com/video1");
        assertThat(result.notes()).contains("vk_video_as_link");
    }

    @Test
    void vkAudioSkipped() {
        Map<String, Object> config = CrosspostConfigSupport.withDefaults(Map.of());
        CrosspostCandidate c = CrosspostCandidate.builder()
                .network("vk")
                .remoteId("1")
                .text("post")
                .attachments(List.of(Map.of("type", "audio", "url", "https://vk.com/audio1")))
                .hasAudio(true)
                .build();
        var result = CrosspostTransformer.transform(c, config, Map.of(), "tg", null);
        assertThat(result.attachments()).isEmpty();
        assertThat(result.notes()).contains("vk_audio_skipped");
    }

    @Test
    void publishAsVkRepostOption() {
        Map<String, Object> config = CrosspostConfigSupport.withDefaults(
                Map.of("transform", Map.of("publish_as_vk_repost", true)));
        var result = CrosspostTransformer.transform(candidate("t"), config, Map.of(), "vk", null);
        assertThat(result.options()).containsEntry("publishAsRepost", true);
    }

    @Test
    void targetOptionsOverride() {
        Map<String, Object> config = CrosspostConfigSupport.withDefaults(Map.of());
        var result = CrosspostTransformer.transform(
                candidate("t"), config, Map.of("close_comments", true), "ok", null);
        assertThat(result.options()).containsEntry("closeComments", true);
    }

    private static CrosspostCandidate candidate(String text) {
        return CrosspostCandidate.builder().network("vk").remoteId("1").text(text).build();
    }
}
