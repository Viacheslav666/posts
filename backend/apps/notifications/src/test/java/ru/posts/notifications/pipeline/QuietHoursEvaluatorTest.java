package ru.posts.notifications.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import ru.posts.persistence.entity.NotificationSettingsEntity;

class QuietHoursEvaluatorTest {

    @Test
    void detectsDaytimeQuietWindow() {
        assertThat(QuietHoursEvaluator.isWithinQuietHours(LocalTime.of(10, 0), LocalTime.of(9, 0), LocalTime.of(17, 0)))
                .isTrue();
        assertThat(QuietHoursEvaluator.isWithinQuietHours(LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(17, 0)))
                .isFalse();
    }

    @Test
    void detectsOvernightQuietWindow() {
        assertThat(QuietHoursEvaluator.isWithinQuietHours(LocalTime.of(23, 0), LocalTime.of(22, 0), LocalTime.of(7, 0)))
                .isTrue();
        assertThat(QuietHoursEvaluator.isWithinQuietHours(LocalTime.of(6, 0), LocalTime.of(22, 0), LocalTime.of(7, 0)))
                .isTrue();
        assertThat(QuietHoursEvaluator.isWithinQuietHours(LocalTime.of(12, 0), LocalTime.of(22, 0), LocalTime.of(7, 0)))
                .isFalse();
    }

    @Test
    void criticalInAppBypassesQuietHours() {
        NotificationSettingsEntity settings = settings(true, LocalTime.of(0, 0), LocalTime.of(23, 59));
        Instant now = Instant.parse("2026-06-01T12:00:00Z");
        assertThat(QuietHoursEvaluator.delayUntil(settings, "publication.failed", NotificationChannels.IN_APP, now))
                .isNull();
        assertThat(QuietHoursEvaluator.delayUntil(settings, "publication.failed", NotificationChannels.EMAIL, now))
                .isNotNull();
    }

    @Test
    void returnsQuietHoursEndInstant() {
        NotificationSettingsEntity settings = settings(true, LocalTime.of(22, 0), LocalTime.of(7, 0));
        settings.setTimezone("Europe/Moscow");
        ZonedDateTime moscow = ZonedDateTime.of(2026, 6, 1, 23, 0, 0, 0, ZoneId.of("Europe/Moscow"));
        Instant delay = QuietHoursEvaluator.delayUntil(
                settings, "publication.succeeded", NotificationChannels.EMAIL, moscow.toInstant());
        assertThat(delay).isNotNull();
        assertThat(delay.atZone(ZoneId.of("Europe/Moscow")).toLocalTime()).isEqualTo(LocalTime.of(7, 0));
    }

    private static NotificationSettingsEntity settings(boolean enabled, LocalTime start, LocalTime end) {
        NotificationSettingsEntity entity = new NotificationSettingsEntity();
        entity.setQuietHoursEnabled(enabled);
        entity.setQuietHoursStart(start);
        entity.setQuietHoursEnd(end);
        entity.setTimezone("Europe/Moscow");
        return entity;
    }
}
