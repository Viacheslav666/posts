package ru.posts.suggest.bulk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.SuggestBulkJobEntity;
import ru.posts.persistence.entity.SuggestItemEntity;
import ru.posts.persistence.entity.SuggestSettingsEntity;
import ru.posts.persistence.repository.SuggestBulkJobRepository;
import ru.posts.persistence.repository.SuggestItemRepository;
import ru.posts.persistence.repository.SuggestSettingsRepository;
import ru.posts.suggest.SuggestProcessingService;

@ExtendWith(MockitoExtension.class)
class SuggestBulkIdorTest {

    @Mock
    SuggestBulkJobRepository jobRepository;

    @Mock
    SuggestItemRepository itemRepository;

    @Mock
    SuggestSettingsRepository settingsRepository;

    @Mock
    SuggestProcessingService processingService;

    @Test
    void approveIdsIgnoresItemsFromAnotherSettingsOrWorkspace() {
        UUID workspaceId = UuidV7.generate();
        UUID settingsId = UuidV7.generate();
        UUID ownId = UuidV7.generate();
        UUID foreignId = UuidV7.generate();

        SuggestBulkJobEntity job = new SuggestBulkJobEntity();
        job.setId(UuidV7.generate());
        job.setWorkspaceId(workspaceId);
        job.setSettingsId(settingsId);
        job.setChannelId(UuidV7.generate());
        job.setAction("reject_ids");
        job.setStatus("pending");
        job.setItemIds(new UUID[] {foreignId, ownId});
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SuggestSettingsEntity settings = new SuggestSettingsEntity();
        settings.setId(settingsId);
        settings.setWorkspaceId(workspaceId);
        when(settingsRepository.findById(settingsId)).thenReturn(Optional.of(settings));

        SuggestItemEntity foreign = new SuggestItemEntity();
        foreign.setId(foreignId);
        foreign.setSettingsId(UuidV7.generate());
        foreign.setWorkspaceId(workspaceId);
        foreign.setStatus("pending_moderation");
        SuggestItemEntity own = new SuggestItemEntity();
        own.setId(ownId);
        own.setSettingsId(settingsId);
        own.setWorkspaceId(workspaceId);
        own.setStatus("pending_moderation");
        when(itemRepository.findById(foreignId)).thenReturn(Optional.of(foreign));
        when(itemRepository.findById(ownId)).thenReturn(Optional.of(own));

        new SuggestBulkProcessor(jobRepository, itemRepository, settingsRepository, processingService)
                .process(job.getId());

        verify(processingService, never()).reject(eqItem(foreign), any(), any(), anyBoolean());
        verify(processingService).reject(own, settings, job.getCreatedBy(), true);
    }

    private static SuggestItemEntity eqItem(SuggestItemEntity item) {
        return org.mockito.ArgumentMatchers.eq(item);
    }
}
