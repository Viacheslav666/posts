package ru.posts.suggest.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.SuggestItemEntity;
import ru.posts.persistence.entity.SuggestSettingsEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.SuggestItemRepository;
import ru.posts.persistence.repository.SuggestSettingsRepository;
import ru.posts.suggest.SuggestProcessingService;
import ru.posts.suggest.metrics.SuggestMetrics;
import ru.posts.suggest.sync.SuggestSyncService.SuggestTokenResolver;
import ru.posts.suggest.vk.SuggestVkClient;
import ru.posts.suggest.vk.SuggestVkClient.SuggestWallPost;

@ExtendWith(MockitoExtension.class)
class SuggestSyncServiceTest {

    @Mock
    SuggestSettingsRepository settingsRepository;

    @Mock
    SuggestItemRepository itemRepository;

    @Mock
    ChannelRepository channelRepository;

    @Mock
    SuggestVkClient vkClient;

    @Mock
    SuggestProcessingService processingService;

    @Mock
    SuggestMetrics metrics;

    @Mock
    SuggestTokenResolver tokenResolver;

    SuggestSyncService service;

    @BeforeEach
    void setUp() {
        service = new SuggestSyncService(
                settingsRepository,
                itemRepository,
                channelRepository,
                vkClient,
                processingService,
                metrics,
                tokenResolver);
    }

    @Test
    void networkErrorDoesNotMarkRemovedAndPagesAreFollowed() {
        UUID settingsId = UuidV7.generate();
        UUID channelId = UuidV7.generate();
        SuggestSettingsEntity settings = new SuggestSettingsEntity();
        settings.setId(settingsId);
        settings.setChannelId(channelId);
        settings.setCreatedBy(UuidV7.generate());
        ChannelEntity channel = new ChannelEntity();
        channel.setId(channelId);
        channel.setExternalId("-100");
        when(settingsRepository.findById(settingsId)).thenReturn(Optional.of(settings));
        when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
        when(tokenResolver.resolveUserToken(channel)).thenReturn("token");

        SuggestWallPost first = new SuggestWallPost("1", "2", "a", List.of(), false, false, "suggest", Map.of());
        SuggestWallPost second = new SuggestWallPost("2", "2", "b", List.of(), false, false, "suggest", Map.of());
        when(vkClient.getSuggests("-100", "token", 2, 0)).thenReturn(List.of(first, second));
        when(vkClient.getSuggests("-100", "token", 2, 2)).thenReturn(List.of());
        when(itemRepository.findByChannelIdAndVkPostId(any(), anyString())).thenReturn(Optional.empty());

        SuggestItemEntity broken = new SuggestItemEntity();
        broken.setVkPostId("900");
        broken.setStatus("pending_moderation");
        SuggestItemEntity gone = new SuggestItemEntity();
        gone.setVkPostId("901");
        gone.setStatus("scheduled");
        when(itemRepository.findByChannelIdAndStatusIn(eq(channelId), any())).thenReturn(List.of(broken, gone));
        when(vkClient.getById("-100", "900", "token")).thenThrow(new IllegalStateException("timeout"));
        when(vkClient.getById("-100", "901", "token")).thenReturn(null);
        when(itemRepository.findBySettingsIdAndStatusOrderByCreatedAtAsc(settingsId, "pending_moderation"))
                .thenReturn(List.of());
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SuggestSyncService.SuggestSyncResult result = service.syncChannel(settingsId, 2);

        assertThat(broken.getStatus()).isEqualTo("pending_moderation");
        assertThat(gone.getStatus()).isEqualTo("removed_externally");
        assertThat(result.removedExternally()).isEqualTo(1);
        verify(vkClient).getSuggests("-100", "token", 2, 0);
        verify(vkClient).getSuggests("-100", "token", 2, 2);
        verify(itemRepository, never()).save(broken);
    }
}
