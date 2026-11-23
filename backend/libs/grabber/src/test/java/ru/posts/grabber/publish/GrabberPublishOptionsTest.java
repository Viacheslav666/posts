package ru.posts.grabber.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.transform.MacroExpander;

class GrabberPublishOptionsTest {

    @Test
    void buildsCommentButtonAndDeleteTimer() {
        Map<String, Object> config = config(Map.of(), "immediate");
        Instant at = Instant.parse("2026-06-01T10:00:00Z");
        GrabberPublishOptions.Built built = GrabberPublishOptions.build(
                config, MacroExpander.Context.of("Клуб", "-5", "wall-5_1", "текст"), at, true);

        assertThat(built.options()).containsEntry("firstComment", "коммент wall-5_1");
        assertThat(built.options()).containsEntry("firstCommentAttachments", List.of("photo-1_2"));
        assertThat(built.options()).containsEntry("closeComments", true);
        assertThat(built.options().get("deleteAfter")).isEqualTo("2026-06-01T12:00:00Z");
        assertThat(built.options()).containsKey("actionButton");
        assertThat(built.notices()).isEmpty();
    }

    @Test
    void postponedModeSkipsCommentButtonAndDelete() {
        Map<String, Object> config = config(Map.of("posting_mode", "vk_postponed"), "vk_postponed");
        GrabberPublishOptions.Built built = GrabberPublishOptions.build(
                config,
                MacroExpander.Context.of("Клуб", "-5", "wall-5_1", "текст"),
                Instant.parse("2026-06-01T10:00:00Z"),
                true);

        assertThat(built.options()).doesNotContainKey("firstComment");
        assertThat(built.options()).doesNotContainKey("actionButton");
        assertThat(built.options()).doesNotContainKey("deleteAfter");
        assertThat(built.options()).containsEntry("closeComments", true);
        assertThat(built.notices())
                .extracting(GrabberPublishOptions.Notice::setting)
                .contains("first_comment", "comment_attachments", "action_button", "delete_after");
    }

    @Test
    void intervalKeepsCommentTextAndDropsAttachments() {
        Map<String, Object> schedule = new HashMap<>();
        schedule.put("posting_mode", "interval");
        Map<String, Object> config = config(Map.of(), "immediate");
        config.put("schedule", schedule);
        GrabberPublishOptions.Built built = GrabberPublishOptions.build(
                config, MacroExpander.Context.of("Клуб", "-5", "wall-5_1", "текст"), Instant.now(), true);

        assertThat(GrabberModeGate.postingMode(config)).isEqualTo("interval");
        assertThat(GrabberModeGate.MATRIX_NOTE).contains("matrix");
        assertThat(built.options()).containsEntry("firstComment", "коммент wall-5_1");
        assertThat(built.options()).doesNotContainKey("firstCommentAttachments");
        assertThat(built.notices())
                .extracting(GrabberPublishOptions.Notice::code)
                .contains("mode_interval");
    }

    @Test
    void delayScheduleStillAllowsFollowUpActions() {
        assertThat(GrabberModeGate.allows(GrabberModeGate.Feature.FIRST_COMMENT, Map.of("schedule_mode", "delay")))
                .isTrue();
        assertThat(GrabberModeGate.allows(GrabberModeGate.Feature.SCHEDULED_DELETE, Map.of("schedule_mode", "slot")))
                .isTrue();
    }

    @Test
    void actionButtonStaysOffUntilTheFlagIsOn() {
        Map<String, Object> config = config(Map.of(), "immediate");
        GrabberPublishOptions.Built built = GrabberPublishOptions.build(
                config, MacroExpander.Context.of("Клуб", "-5", "wall-5_1", "текст"), Instant.now(), false);

        assertThat(built.options()).doesNotContainKey("actionButton");
        assertThat(built.notices())
                .extracting(GrabberPublishOptions.Notice::code)
                .contains("disabled");
    }

    @Test
    void emptyExpandedCommentReportsDroppedAttachments() {
        Map<String, Object> config = new HashMap<>();
        config.put(
                "first_comment",
                Map.of("enabled", true, "text", "[keyText]absent,hello[/keyText]", "attachments", "photo-1_2"));
        GrabberPublishOptions.Built built = GrabberPublishOptions.build(
                config, MacroExpander.Context.of("Клуб", "-5", "wall-5_1", "текст без слова"), Instant.now(), true);

        assertThat(built.options()).doesNotContainKey("firstComment");
        assertThat(built.options()).doesNotContainKey("firstCommentAttachments");
        assertThat(built.notices())
                .extracting(GrabberPublishOptions.Notice::setting)
                .contains("first_comment", "comment_attachments");
        assertThat(built.notices())
                .extracting(GrabberPublishOptions.Notice::code)
                .contains("empty_after_macros");
    }

    @Test
    void stripsClientAiPriceAndRejectsLongButtonUrl() {
        Map<String, Object> transform = new HashMap<>();
        transform.put("ai_model", "stub");
        transform.put("ai_cost_kopecks", 1);
        Map<String, Object> config = new HashMap<>();
        config.put("transform", transform);
        GrabberConfigSupport.validateSettings(config);
        assertThat(GrabberConfigSupport.transform(config)).doesNotContainKey("ai_cost_kopecks");

        String url = "https://example.com/" + "a".repeat(2040);
        assertThat(url).hasSizeGreaterThan(GrabberPublishOptions.ACTION_BUTTON_URL_MAX);
        assertThat(GrabberPublishOptions.actionButtonError(Map.of("type", "open_url", "title", "Открыть", "url", url)))
                .contains("2048");
    }

    @Test
    void defaultsNoLongerBlockFirstComment() {
        Map<String, Object> config = GrabberConfigSupport.withDefaults(new HashMap<>());
        assertThat(GrabberConfigSupport.firstComment(config)).containsEntry("pending_be23", false);
        assertThat(GrabberConfigSupport.appendAuthorSignature(config)).isTrue();
    }

    private static Map<String, Object> config(Map<String, Object> extra, String scheduleMode) {
        Map<String, Object> transform = new HashMap<>();
        transform.put("close_comments", true);
        transform.put("delete_after_enabled", true);
        transform.put("delete_after_amount", 2);
        transform.put("delete_after_unit", "hours");
        transform.put("action_button", Map.of("type", "open_url", "title", "Открыть", "url", "https://example.com/a"));
        Map<String, Object> config = new HashMap<>();
        config.put("schedule_mode", scheduleMode);
        config.put("transform", transform);
        config.put(
                "first_comment",
                Map.of("enabled", true, "text", "коммент [post_id_of_the_source]", "attachments", "photo-1_2"));
        config.putAll(extra);
        return config;
    }
}
