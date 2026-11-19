package ru.posts.antispam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.posts.antispam.metrics.AntispamMetrics;
import ru.posts.antispam.model.AntispamAction;
import ru.posts.antispam.model.AntispamSection;
import ru.posts.antispam.model.Verdict;
import ru.posts.persistence.jdbc.AntispamLogJdbc;
import ru.posts.persistence.jdbc.OutboxJdbc;
import ru.posts.persistence.service.RemoteActionService;
import ru.posts.social.inbound.SocialEventKind;
import ru.posts.social.inbound.SocialInboundAuthor;
import ru.posts.social.inbound.SocialInboundEvent;

@ExtendWith(MockitoExtension.class)
class AntispamActionOrderTest {

    @Mock
    RemoteActionService remoteActionService;

    @Mock
    AntispamLogJdbc logJdbc;

    @Mock
    OutboxJdbc outboxJdbc;

    @Test
    void replyIsEnqueuedBeforeDeleteAndDeleteWaitsForIt() {
        when(logJdbc.findIdByDedup(any(), any(), any())).thenReturn(Optional.empty());
        when(logJdbc.countActionsSince(any(), any())).thenReturn(0L);
        when(logJdbc.toJson(any())).thenReturn("{}");
        when(logJdbc.insert(any())).thenReturn(UUID.randomUUID());
        when(remoteActionService.enqueue(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(UUID.randomUUID());

        UUID channelId = UUID.randomUUID();
        SocialInboundEvent event = new SocialInboundEvent(
                "vk",
                channelId,
                UUID.randomUUID(),
                SocialEventKind.COMMENT_NEW,
                "d",
                Map.of("owner_id", "-1", "post_id", "10", "comment_id", "77"),
                new SocialInboundAuthor("5", false, false),
                "spam",
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                "{}",
                List.of("antispam"));
        Verdict verdict = Verdict.hit(
                UUID.randomUUID(),
                "rule",
                false,
                List.of("keyword"),
                List.of(AntispamAction.delete(), AntispamAction.reply("removed")));

        new AntispamActionApplier(
                        remoteActionService,
                        logJdbc,
                        outboxJdbc,
                        new ObjectMapper(),
                        new AntispamMetrics(new SimpleMeterRegistry()),
                        new RemoteRefFactory(new ObjectMapper()))
                .apply(event, AntispamSection.WALL_COMMENT, verdict);

        ArgumentCaptor<String> kinds = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloads = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> runAt = ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(remoteActionService, org.mockito.Mockito.times(2))
                .enqueue(
                        kinds.capture(),
                        any(),
                        any(),
                        any(),
                        payloads.capture(),
                        any(),
                        runAt.capture(),
                        any(),
                        any(),
                        any());
        assertThat(kinds.getAllValues()).containsExactly("reply_comment", "delete_comment");
        assertThat(runAt.getAllValues().get(0)).isBefore(runAt.getAllValues().get(1));
        assertThat(payloads.getAllValues().get(1)).contains("waitForIdempotencyKey");
        assertThat(payloads.getAllValues().get(1)).contains(":reply");
    }
}
