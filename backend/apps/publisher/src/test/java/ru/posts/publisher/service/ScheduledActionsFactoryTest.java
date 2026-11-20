package ru.posts.publisher.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.posts.ord.api.AdMarkingDefaults;
import ru.posts.persistence.jdbc.PublicationClaim;
import ru.posts.persistence.jdbc.ScheduledActionLifecycleJdbc;

@ExtendWith(MockitoExtension.class)
class ScheduledActionsFactoryTest {

    @Mock
    private ScheduledActionLifecycleJdbc actionsJdbc;

    private ScheduledActionsFactory factory;

    @BeforeEach
    void setUp() {
        factory = new ScheduledActionsFactory(actionsJdbc, new ObjectMapper());
    }

    @Test
    void doesNotScheduleUnpinWithoutPin() {
        UUID publicationId = UUID.randomUUID();
        Instant runAt = Instant.parse("2026-06-01T10:00:00Z");
        PublicationClaim claim = claim(publicationId, runAt, "{\"unpinAt\":\"2026-06-02T10:00:00Z\"}", false, 0, false);

        factory.createAfterSuccess(claim, "remote-1");

        verify(actionsJdbc, never()).insertAction(any(), any(), any(), eq("unpin"), any(), any());
    }

    @Test
    void defaultAdDeleteAfterUsesPublishTimePlusPeriod() {
        UUID publicationId = UUID.randomUUID();
        Instant runAt = Instant.parse("2026-06-01T10:00:00Z");
        PublicationClaim claim = claim(publicationId, runAt, "{}", true, 0, true);

        factory.createAfterSuccess(claim, "remote-2");

        ArgumentCaptor<Instant> runAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(actionsJdbc)
                .insertAction(eq(publicationId), eq(runAt), any(), eq("delete"), runAtCaptor.capture(), any());

        Instant expected =
                runAt.plusSeconds(AdMarkingDefaults.DEFAULT_DISPLAY_PERIOD_DAYS * AdMarkingDefaults.SECONDS_PER_DAY);
        assertThat(runAtCaptor.getValue()).isEqualTo(expected);
    }

    private static PublicationClaim claim(
            UUID id, Instant runAt, String optionsJson, boolean ad, int displayDays, boolean autoDelete) {
        return new PublicationClaim(
                id,
                runAt,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "vk",
                "idem",
                "-1",
                "text",
                null,
                0,
                "post",
                optionsJson,
                ad,
                "manual",
                "erid",
                "adv",
                "inn",
                displayDays,
                autoDelete,
                null,
                null,
                null,
                "photo",
                "[]",
                "Europe/Moscow",
                "title",
                "[]",
                null,
                null);
    }
}
