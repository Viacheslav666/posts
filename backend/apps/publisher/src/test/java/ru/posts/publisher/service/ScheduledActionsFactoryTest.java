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
    void doesNotScheduleAdDeleteWhenAutoDeleteDisabled() {
        UUID publicationId = UUID.randomUUID();
        Instant runAt = Instant.parse("2026-06-01T10:00:00Z");
        PublicationClaim claim = claim(publicationId, runAt, "{}", true, 30, false);

        factory.createAfterSuccess(claim, "remote-off");

        verify(actionsJdbc, never()).insertAction(any(), any(), any(), eq("delete"), any(), any());
    }

    @Test
    void deleteAfterComputedPerPublicationRunAt() {
        UUID pub1 = UUID.randomUUID();
        UUID pub2 = UUID.randomUUID();
        Instant run1 = Instant.parse("2026-06-01T10:00:00Z");
        Instant run2 = Instant.parse("2026-06-15T10:00:00Z");
        PublicationClaim claim1 = claim(pub1, run1, "{}", true, 7, true);
        PublicationClaim claim2 = claim(pub2, run2, "{}", true, 7, true);

        factory.createAfterSuccess(claim1, "r1");
        factory.createAfterSuccess(claim2, "r2");

        ArgumentCaptor<Instant> c1 = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> c2 = ArgumentCaptor.forClass(Instant.class);
        verify(actionsJdbc).insertAction(eq(pub1), eq(run1), any(), eq("delete"), c1.capture(), any());
        verify(actionsJdbc).insertAction(eq(pub2), eq(run2), any(), eq("delete"), c2.capture(), any());

        assertThat(c1.getValue()).isEqualTo(run1.plusSeconds(7 * AdMarkingDefaults.SECONDS_PER_DAY));
        assertThat(c2.getValue()).isEqualTo(run2.plusSeconds(7 * AdMarkingDefaults.SECONDS_PER_DAY));
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

    @Test
    void firstCommentPayloadIncludesAttachments() {
        UUID publicationId = UUID.randomUUID();
        Instant runAt = Instant.parse("2026-06-01T10:00:00Z");
        PublicationClaim claim = claim(
                publicationId,
                runAt,
                "{\"firstComment\":\"привет\",\"firstCommentAttachments\":[\"photo-1_2\",\"video-3_4\"]}",
                false,
                0,
                false);

        factory.createAfterSuccess(claim, "remote-9");

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(actionsJdbc)
                .insertAction(eq(publicationId), eq(runAt), any(), eq("first_comment"), any(), payload.capture());
        assertThat(payload.getValue()).contains("привет").contains("photo-1_2").contains("video-3_4");
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
