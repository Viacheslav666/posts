package ru.posts.suggest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.PostEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.entity.SuggestItemEntity;
import ru.posts.persistence.entity.SuggestSettingsEntity;
import ru.posts.persistence.jdbc.SocialRemoteActionLifecycleJdbc.ActionRef;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.PostRepository;
import ru.posts.persistence.repository.PublicationRepository;
import ru.posts.persistence.repository.SuggestItemRepository;
import ru.posts.persistence.repository.SuggestSettingsRepository;
import ru.posts.persistence.service.RemoteActionService;
import ru.posts.suggest.filter.SuggestAction;
import ru.posts.suggest.filter.SuggestDecision;
import ru.posts.suggest.filter.SuggestFilterEngine;
import ru.posts.suggest.metrics.SuggestMetrics;
import ru.posts.suggest.schedule.SuggestSlotScheduler;

@ExtendWith(MockitoExtension.class)
class SuggestProcessingServiceTest {

    @Mock
    SuggestSettingsRepository settingsRepository;

    @Mock
    SuggestItemRepository itemRepository;

    @Mock
    ChannelRepository channelRepository;

    @Mock
    SuggestFilterEngine filterEngine;

    @Mock
    RemoteActionService remoteActionService;

    @Mock
    SuggestMetrics metrics;

    @Mock
    PublicationRepository publicationRepository;

    @Mock
    PostRepository postRepository;

    SuggestProcessingService service;

    @BeforeEach
    void setUp() {
        service = new SuggestProcessingService(
                settingsRepository,
                itemRepository,
                channelRepository,
                filterEngine,
                new SuggestSlotScheduler(),
                remoteActionService,
                metrics,
                new ObjectMapper(),
                publicationRepository,
                postRepository);
    }

    @Test
    void authorLimitCountHappensAfterTheSettingsLock() {
        UUID channelId = UuidV7.generate();
        SuggestSettingsEntity settings = settings(channelId);
        when(settingsRepository.findByChannelId(channelId)).thenReturn(Optional.of(settings));
        when(settingsRepository.findByIdForUpdate(settings.getId())).thenReturn(Optional.of(settings));
        when(itemRepository.findByChannelIdAndVkPostId(channelId, "42")).thenReturn(Optional.empty());
        when(itemRepository.countByChannelIdAndAuthorVkIdAndStatusInAndCreatedAtAfter(any(), anyString(), any(), any()))
                .thenReturn(0L);
        when(itemRepository.countByChannelIdAndStatusInAndCreatedAtAfter(any(), any(), any()))
                .thenReturn(0L);
        when(itemRepository.findRecentWithSimhashByChannel(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(itemRepository.findFirstByChannelIdAndStatusInOrderByDecidedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(publicationRepository.findLatestSucceeded(channelId)).thenReturn(List.of());
        when(filterEngine.evaluate(any(), any(), any()))
                .thenReturn(SuggestDecision.of(SuggestAction.MODERATE, List.of()));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processSuggested(channelId, "42", "author", "hello", List.of(), null, null, null, null);

        InOrder order = inOrder(settingsRepository, itemRepository);
        order.verify(settingsRepository).findByIdForUpdate(settings.getId());
        order.verify(itemRepository).findByChannelIdAndVkPostId(channelId, "42");
        order.verify(itemRepository, times(3))
                .countByChannelIdAndAuthorVkIdAndStatusInAndCreatedAtAfter(
                        eq(channelId), eq("author"), any(Set.class), any());
    }

    @Test
    void adsPauseUsesTheLaterWallAdNotTheEarlierSuggest() {
        UUID channelId = UuidV7.generate();
        SuggestSettingsEntity settings = settings(channelId);
        SuggestItemEntity item = item(channelId, settings.getId(), "7");
        item.setStatus("pending_moderation");

        SuggestItemEntity earlier = new SuggestItemEntity();
        earlier.setText("organic");
        earlier.setDecidedAt(Instant.now().minus(3, ChronoUnit.HOURS));
        when(itemRepository.findFirstByChannelIdAndStatusInOrderByDecidedAtDesc(eq(channelId), any()))
                .thenReturn(Optional.of(earlier));

        UUID postId = UuidV7.generate();
        PublicationEntity wall = new PublicationEntity();
        wall.setPostId(postId);
        wall.setPublishedAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        when(publicationRepository.findLatestSucceeded(channelId)).thenReturn(List.of(wall));
        PostEntity ad = new PostEntity();
        ad.setAd(true);
        when(postRepository.findById(postId)).thenReturn(Optional.of(ad));
        when(settingsRepository.findByIdForUpdate(settings.getId())).thenReturn(Optional.of(settings));
        when(channelRepository.findById(channelId)).thenReturn(Optional.empty());
        when(remoteActionService.enqueue(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(UuidV7.generate());
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SuggestItemEntity saved = service.approve(item, settings, null, null, null, null);

        assertThat(saved.getScheduledFor()).isAfter(Instant.now().plus(20, ChronoUnit.MINUTES));
    }

    @Test
    void rejectOfPublishedOrRunningPublishConflictsAndPendingIsCancelled() {
        UUID channelId = UuidV7.generate();
        SuggestSettingsEntity settings = settings(channelId);
        SuggestItemEntity published = item(channelId, settings.getId(), "1");
        published.setStatus("published");
        assertThatThrownBy(() -> service.reject(published, settings, null, true))
                .isInstanceOf(SuggestConflictException.class);
        verify(remoteActionService, never())
                .enqueue(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        SuggestItemEntity running = item(channelId, settings.getId(), "2");
        running.setStatus("scheduled");
        when(remoteActionService.findLatest("suggest:" + channelId + ":2"))
                .thenReturn(Optional.of(new ActionRef(UuidV7.generate(), "in_progress", Instant.now())));
        assertThatThrownBy(() -> service.reject(running, settings, null, true))
                .isInstanceOf(SuggestConflictException.class);

        SuggestItemEntity waiting = item(channelId, settings.getId(), "3");
        waiting.setStatus("scheduled");
        UUID actionId = UuidV7.generate();
        when(remoteActionService.findLatest("suggest:" + channelId + ":3"))
                .thenReturn(Optional.of(new ActionRef(UuidV7.generate(), "pending", Instant.now())));
        when(channelRepository.findById(channelId)).thenReturn(Optional.empty());
        when(remoteActionService.enqueue(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(actionId);
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SuggestItemEntity saved = service.reject(waiting, settings, null, true);

        verify(remoteActionService).cancelIfIdle("suggest:" + channelId + ":3");
        assertThat(saved.getRemoteActionId()).isEqualTo(actionId);
    }

    private static SuggestSettingsEntity settings(UUID channelId) {
        SuggestSettingsEntity settings = new SuggestSettingsEntity();
        settings.setId(UuidV7.generate());
        settings.setWorkspaceId(UuidV7.generate());
        settings.setChannelId(channelId);
        settings.setStatus("active");
        settings.setMode("auto");
        return settings;
    }

    private static SuggestItemEntity item(UUID channelId, UUID settingsId, String vkPostId) {
        SuggestItemEntity item = new SuggestItemEntity();
        item.setId(UuidV7.generate());
        item.setWorkspaceId(UuidV7.generate());
        item.setChannelId(channelId);
        item.setSettingsId(settingsId);
        item.setVkPostId(vkPostId);
        return item;
    }
}
