package ru.posts.grabber.transform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.posts.grabber.config.GrabberConfigSupport;

class ContentTransformerTest {

    @Test
    @DisplayName("strip hashtags, mentions, phones, links, all text")
    void stripTextTransforms() {
        Map<String, Object> config = GrabberConfigSupport.withDefaults(Map.of(
                "transform",
                Map.of(
                        "strip_hashtags", true,
                        "strip_mentions", true,
                        "strip_phones", true,
                        "strip_all_links", true)));
        String text = ContentTransformer.transform(
                "Привет #новости @admin см https://example.com и +7 999 111-22-33", config);
        assertThat(text).doesNotContain("#новости").doesNotContain("@admin");
        assertThat(text).doesNotContain("example.com").doesNotContain("999");
        assertThat(text).contains("Привет");

        Map<String, Object> wipe =
                GrabberConfigSupport.withDefaults(Map.of("transform", Map.of("strip_all_text", true)));
        assertThat(ContentTransformer.transform("любой текст", wipe)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "strip_photo,photo",
        "strip_video,video",
        "strip_clip,clip",
        "strip_audio,audio",
        "strip_poll,poll",
        "strip_doc,doc",
        "strip_market,market",
        "strip_wiki,page",
        "strip_album,album",
        "strip_playlist,playlist",
        "strip_market_album,market_album"
    })
    void stripAttachmentTypes(String flag, String type) {
        Map<String, Object> config = GrabberConfigSupport.withDefaults(Map.of("transform", Map.of(flag, true)));
        ContentTransformer.TransformResult result = ContentTransformer.transform(
                "текст",
                List.of(Map.of("type", type), Map.of("type", "link")),
                config,
                MacroExpander.Context.of("n", "id", "wall-1_1"));
        assertThat(result.attachments()).extracting(a -> a.get("type")).doesNotContain(type);
        if (!"link".equals(type)) {
            assertThat(result.attachments()).extracting(a -> a.get("type")).contains("link");
        }
    }

    @Test
    void wordReplaceAndRemoveKeywordsRussian() {
        Map<String, Object> config = GrabberConfigSupport.withDefaults(Map.of(
                "transform",
                Map.of(
                        "word_replacements",
                        Map.of("впостер", "Posts", "VPoster", "Posts"),
                        "remove_keywords",
                        List.of("срочно"))));
        String text = ContentTransformer.transform("Срочно: впостер лучше VPoster", config);
        assertThat(text.toLowerCase(Locale.ROOT)).doesNotContain("срочно");
        assertThat(text).contains("Posts");
    }

    @Test
    void macrosInPrependAppend() {
        Map<String, Object> config = GrabberConfigSupport.withDefaults(Map.of(
                "transform",
                Map.of(
                        "prepend_text",
                        "Источник [name_of_the_source] ([id_of_the_source])",
                        "append_text",
                        "пост [post_id_of_the_source] [rand]а|а[/rand]")));
        ContentTransformer.TransformResult result = ContentTransformer.transform(
                "тело", List.of(), config, MacroExpander.Context.of("Новости", "-123", "wall-123_9"));
        assertThat(result.text()).contains("Источник Новости (-123)");
        assertThat(result.text()).contains("пост wall-123_9");
        assertThat(result.text()).contains("тело");
    }

    @Test
    void attachWhenNoAttachmentsOnly() {
        Map<String, Object> config = GrabberConfigSupport.withDefaults(Map.of(
                "transform",
                Map.of(
                        "attach_when_no_attachments",
                        true,
                        "attached_attachments",
                        List.of(Map.of("type", "photo", "id", "photo1")))));
        ContentTransformer.TransformResult empty =
                ContentTransformer.transform("t", List.of(), config, MacroExpander.Context.of("", "", ""));
        assertThat(empty.attachments()).hasSize(1);
        ContentTransformer.TransformResult nonempty = ContentTransformer.transform(
                "t", List.of(Map.of("type", "video")), config, MacroExpander.Context.of("", "", ""));
        assertThat(nonempty.attachments())
                .hasSize(1)
                .extracting(a -> a.get("type"))
                .containsExactly("video");
    }

    @Test
    void firstCommentDraftPendingBe23() {
        Map<String, Object> config = GrabberConfigSupport.withDefaults(Map.of(
                "first_comment",
                Map.of("enabled", true, "text", "Коммент от [name_of_the_source]", "pending_be23", true)));
        String draft = ContentTransformer.firstCommentDraft(config, MacroExpander.Context.of("Src", "-1", "wall-1_1"));
        assertThat(draft).isEqualTo("Коммент от Src");
        assertThat(GrabberConfigSupport.firstComment(config).get("pending_be23"))
                .isEqualTo(true);
    }
}
