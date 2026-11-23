package ru.posts.grabber.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.posts.grabber.config.GrabberConfigSupport;

class GrabberModeMatrixTest {

    @Test
    void postponedModesDisableActionCommentDeleteAndInterval() {
        for (String mode : List.of("vk_postponed_post", "vk_postponed_repost")) {
            assertThat(GrabberModeMatrix.supports(mode, 0, GrabberModeMatrix.Feature.ACTION_BUTTON))
                    .isFalse();
            assertThat(GrabberModeMatrix.supports(mode, 0, GrabberModeMatrix.Feature.FIRST_COMMENT))
                    .isFalse();
            assertThat(GrabberModeMatrix.supports(mode, 0, GrabberModeMatrix.Feature.COMMENT_ATTACHMENTS))
                    .isFalse();
            assertThat(GrabberModeMatrix.supports(mode, 0, GrabberModeMatrix.Feature.SCHEDULED_DELETE))
                    .isFalse();
            assertThat(GrabberModeMatrix.supports(mode, 0, GrabberModeMatrix.Feature.INTERVAL))
                    .isFalse();
        }
    }

    @Test
    void immediateModesAllowFeaturesButIntervalDisablesCommentAttachments() {
        for (String mode : List.of("wall_now", "wall_repost")) {
            assertThat(GrabberModeMatrix.supports(mode, 0, GrabberModeMatrix.Feature.COMMENT_ATTACHMENTS))
                    .isTrue();
            assertThat(GrabberModeMatrix.supports(mode, 15, GrabberModeMatrix.Feature.COMMENT_ATTACHMENTS))
                    .isFalse();
            assertThat(GrabberModeMatrix.supports(mode, 15, GrabberModeMatrix.Feature.FIRST_COMMENT))
                    .isTrue();
            assertThat(GrabberModeMatrix.supports(mode, 15, GrabberModeMatrix.Feature.INTERVAL))
                    .isTrue();
        }
    }

    @Test
    void describeExposesFourModesAndIntervalRule() {
        Map<String, Object> body = GrabberModeMatrix.describe();
        assertThat((List<?>) body.get("modes")).hasSize(4);
        assertThat(body.get("intervalDisables")).isEqualTo(List.of("commentAttachments"));
        assertThat(body.get("postponedHorizonDays")).isEqualTo(7);
        assertThat(body.get("defaultMaxAgeDays")).isEqualTo(60);
        assertThat(body.get("maxSources")).isEqualTo(20);
    }

    @Test
    void rejectsIntervalAndFirstCommentInPostponedMode() {
        Map<String, Object> config = GrabberConfigSupport.withDefaults(Map.of(
                "target_mode",
                "vk_postponed_post",
                "limits",
                Map.of("min_interval_minutes", 10),
                "first_comment",
                Map.of("enabled", true, "text", "hi", "attachments", List.of("photo1"))));
        assertThatThrownBy(() -> GrabberModeRestrictions.validate(config, "grabber"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Интервал");
    }

    @Test
    void rejectsCommentAttachmentsWhenIntervalIsSet() {
        Map<String, Object> config = GrabberConfigSupport.withDefaults(Map.of(
                "target_mode",
                "wall_now",
                "limits",
                Map.of("min_interval_minutes", 5),
                "first_comment",
                Map.of("enabled", true, "attachments", List.of("photo1"))));
        assertThatThrownBy(() -> GrabberModeRestrictions.validate(config, "grabber"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("интервалом");
    }

    @Test
    void rejectsRepostForRssAndAllowsZeroLimits() {
        Map<String, Object> repost = GrabberConfigSupport.withDefaults(Map.of("target_mode", "wall_repost"));
        assertThatThrownBy(() -> GrabberModeRestrictions.validate(repost, "rss"))
                .hasMessageContaining("Репост");

        Map<String, Object> open = GrabberConfigSupport.withDefaults(Map.of(
                "target_mode",
                "wall_now",
                "limits",
                Map.of("rate_limit_per_day", 0, "rate_limit_per_source_per_day", 0, "min_interval_minutes", 0),
                "filters",
                Map.of("max_age_days", 0)));
        GrabberModeRestrictions.validate(open, "grabber");
        assertThat(GrabberConfigSupport.maxAgeDays(open)).isZero();
        assertThat(GrabberConfigSupport.rateLimitPerDay(open)).isZero();
    }

    @Test
    void defaultMaxAgeIsSixtyWhenMissing() {
        Map<String, Object> config = GrabberConfigSupport.withDefaults(Map.of());
        assertThat(GrabberConfigSupport.maxAgeDays(config)).isEqualTo(60);
        assertThat(GrabberConfigSupport.targetMode(config)).isEqualTo("wall_now");
        assertThat(GrabberConfigSupport.notifyOnError(config)).isTrue();
        assertThat(config.get("config_version")).isEqualTo(3);
    }

    @Test
    void postponedPublishDateStaysInsideSevenDaysAndWindow() {
        Instant now = Instant.parse("2026-11-18T12:00:00Z");
        Map<String, Object> config = GrabberConfigSupport.withDefaults(Map.of(
                "target_mode",
                "vk_postponed_post",
                "schedule",
                Map.of(
                        "enabled",
                        true,
                        "timezone",
                        "UTC",
                        "days_of_week",
                        List.of(1, 2, 3, 4, 5),
                        "hours_from",
                        9,
                        "hours_to",
                        18)));
        GrabberPublishSchedule.Plan plan = GrabberPublishSchedule.plan(config, now, now, 0);
        assertThat(plan.pipelineRunAt()).isEqualTo(now);
        assertThat(plan.vkPublishDateEpoch()).isNotNull();
        Instant publishAt = Instant.ofEpochSecond(plan.vkPublishDateEpoch());
        assertThat(publishAt).isAfter(now);
        assertThat(publishAt).isBefore(now.plusSeconds(7L * 24 * 3600 + 1));
    }

    @Test
    void immediateIntervalSpacesSlotsAndRepostObjectUsesOwner() {
        Instant now = Instant.parse("2026-11-18T10:00:00Z");
        Map<String, Object> config = GrabberConfigSupport.withDefaults(
                Map.of("target_mode", "wall_now", "limits", Map.of("min_interval_minutes", 10)));
        GrabberPublishSchedule.Plan first = GrabberPublishSchedule.plan(config, now, now, 0);
        GrabberPublishSchedule.Plan second = GrabberPublishSchedule.plan(config, now, now, 1);
        assertThat(second.pipelineRunAt()).isEqualTo(first.pipelineRunAt().plusSeconds(600));
        assertThat(first.vkPublishDateEpoch()).isNull();

        assertThat(GrabberRepostObject.from("-5", "9", Map.of())).isEqualTo("wall-5_9");
        assertThat(GrabberRepostObject.from("club", "9", Map.of("owner_id", -8)))
                .isEqualTo("wall-8_9");
        assertThat(GrabberRepostObject.from("screen", "x", Map.of())).isNull();
    }
}
