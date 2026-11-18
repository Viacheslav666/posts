package ru.posts.notifications.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import ru.posts.messaging.notify.NotifyDomainEvent;
import ru.posts.notifications.metrics.NotificationMetrics;
import ru.posts.persistence.entity.NotificationEntity;
import ru.posts.persistence.repository.NotificationDeliveryRepository;
import ru.posts.persistence.repository.NotificationRepository;
import ru.posts.persistence.repository.NotificationSettingsRepository;

@ExtendWith(MockitoExtension.class)
class DomainEventProcessorDedupeTest {

    @Mock
    RecipientResolver recipientResolver;

    @Mock
    NotificationRepository notificationRepository;

    @Mock
    NotificationDeliveryRepository deliveryRepository;

    @Mock
    NotificationSettingsRepository settingsRepository;

    @Mock
    DeliveryCommandPublisher commandPublisher;

    @Mock
    TransactionTemplate transactionTemplate;

    @Mock
    NotificationMetrics metrics;

    DomainEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DomainEventProcessor(
                recipientResolver,
                notificationRepository,
                deliveryRepository,
                settingsRepository,
                commandPublisher,
                transactionTemplate,
                metrics);
    }

    @Test
    void skipsWhenNotificationAlreadyExists() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        NotifyDomainEvent event = new NotifyDomainEvent(
                1L,
                "publication",
                aggregateId,
                "publication.failed",
                Map.of("workspaceId", workspaceId.toString(), "network", "tg", "result", "auth"),
                "trace");

        when(recipientResolver.resolve(event)).thenReturn(List.of(userId));
        when(recipientResolver.resolveWorkspaceId(event)).thenReturn(workspaceId);
        when(notificationRepository.findByUserIdAndDedupeKey(
                        userId, DomainEventProcessor.buildDedupeKey("publication.failed", aggregateId, userId)))
                .thenReturn(Optional.of(new NotificationEntity()));

        assertThat(processor.process(event)).isTrue();
        verify(transactionTemplate, never()).execute(any());
        verify(commandPublisher, never()).publish(any());
        verify(metrics).record("publication.failed", NotificationChannels.IN_APP, "deduped");
    }

    @Test
    void buildDedupeKeyIncludesTypeAggregateUser() {
        UUID aggregateId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        assertThat(DomainEventProcessor.buildDedupeKey("publication.failed", aggregateId, userId))
                .isEqualTo(
                        "publication.failed:11111111-1111-1111-1111-111111111111:22222222-2222-2222-2222-222222222222");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsNotificationForNewEvent() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        NotifyDomainEvent event = new NotifyDomainEvent(
                2L,
                "publication",
                aggregateId,
                "publication.failed",
                Map.of("workspaceId", workspaceId.toString()),
                "trace");

        when(recipientResolver.resolve(event)).thenReturn(List.of(userId));
        when(recipientResolver.resolveWorkspaceId(event)).thenReturn(workspaceId);
        when(notificationRepository.findByUserIdAndDedupeKey(any(), any())).thenReturn(Optional.empty());
        when(settingsRepository.findById(userId)).thenReturn(Optional.empty());
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        });
        when(notificationRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            NotificationEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
        when(deliveryRepository.existsByDedupeKeyAndChannel(any(), any())).thenReturn(false);
        when(deliveryRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryRepository.findByDedupeKeyAndChannel(any(), any())).thenReturn(Optional.empty());

        assertThat(processor.process(event)).isTrue();
        verify(notificationRepository).saveAndFlush(any());
    }
}
