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
        assertThat(GrabberRepostObject.from("-123", "wall-123_456", Map.of())).isEqualTo("wall-123_456");
    }

    @Test
    void closedWindowSpacesSlotsFromOneOpening() {
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
                        List.of(1, 2, 3, 4, 5, 6, 7),
                        "hours_from",
                        9,
                        "hours_to",
                        10)));
        GrabberPublishSchedule.Plan first = GrabberPublishSchedule.plan(config, now, now, 0);
        GrabberPublishSchedule.Plan second = GrabberPublishSchedule.plan(config, now, now, 1);
        Instant opening = Instant.ofEpochSecond(first.vkPublishDateEpoch());
        Instant next = Instant.ofEpochSecond(second.vkPublishDateEpoch());
        assertThat(opening).isEqualTo(Instant.parse("2026-11-19T09:00:00Z"));
        assertThat(next).isEqualTo(opening.plusSeconds(60));
        assertThat(next).isNotEqualTo(opening);
    }

    @Test
    void rollPastWindowRechecksSevenDayHorizon() {
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
                        List.of(4),
                        "hours_from",
                        9,
                        "hours_to",
                        10)));
        assertThatThrownBy(() -> GrabberPublishSchedule.plan(config, now, now, 60))
                .isInstanceOf(PostponedHorizonException.class);
    }

    @Test
    void intervalAbove120IsRejected() {
        Map<String, Object> config = GrabberConfigSupport.withDefaults(
                Map.of("target_mode", "wall_now", "limits", Map.of("min_interval_minutes", 121)));
        assertThat(GrabberConfigSupport.minIntervalMinutes(config)).isEqualTo(120);
        assertThatThrownBy(() -> GrabberModeRestrictions.validate(config, "grabber"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("120");
    }

    @Test
    void postponedRepostIsUnavailableUntilFlag() {
        Map<String, Object> body = GrabberModeMatrix.describe();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modes = (List<Map<String, Object>>) body.get("modes");
        Map<String, Object> row = modes.stream()
                .filter(item -> "vk_postponed_repost".equals(item.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(row.get("available")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> enabled =
                (List<Map<String, Object>>) GrabberModeMatrix.describe(true).get("modes");
        assertThat(enabled.stream()
                        .filter(item -> "vk_postponed_repost".equals(item.get("id")))
                        .findFirst()
                        .orElseThrow()
                        .get("available"))
                .isEqualTo(true);
        Map<String, Object> config = GrabberConfigSupport.withDefaults(Map.of("target_mode", "vk_postponed_repost"));
        assertThatThrownBy(() -> GrabberModeRestrictions.validate(config, "grabber"))
                .hasMessage(GrabberModeMatrix.POSTPONED_REPOST_UNAVAILABLE_HINT);
        GrabberModeRestrictions.validate(config, "grabber", true);
    }

    @Test
    void scheduleTimezoneUsesTheRuleZone() {
        Map<String, Object> config =
                GrabberConfigSupport.withDefaults(Map.of("schedule", Map.of("timezone", "Europe/Samara")));
        assertThat(GrabberConfigSupport.scheduleTimezone(config)).isEqualTo("Europe/Samara");
        assertThat(GrabberConfigSupport.scheduleTimezone(Map.of())).isEqualTo("Europe/Moscow");
        assertThat(GrabberConfigSupport.scheduleTimezone(Map.of("schedule", Map.of("timezone", "Not/AZone"))))
                .isEqualTo("Europe/Moscow");
    }
}
