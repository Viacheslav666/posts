package ru.posts.api.automation.suggest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ru.posts.api.admin.FeatureFlagResolver;
import ru.posts.api.error.ApiException;
import ru.posts.api.idempotency.IdempotencyService;
import ru.posts.api.web.dto.suggest.SuggestDtos;
import ru.posts.api.workspace.AuditRecorder;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.api.workspace.WorkspaceMembership;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.SuggestItemEntity;
import ru.posts.persistence.entity.SuggestSettingsEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.SuggestBulkJobRepository;
import ru.posts.persistence.repository.SuggestItemRepository;
import ru.posts.persistence.repository.SuggestSettingsRepository;
import ru.posts.security.rbac.RbacRole;
import ru.posts.social.inbound.subscription.EventSubscriptionService;
import ru.posts.suggest.SuggestConflictException;
import ru.posts.suggest.SuggestProcessingService;
import ru.posts.suggest.bulk.SuggestBulkProcessor;
import ru.posts.suggest.sync.SuggestSyncService;

@ExtendWith(MockitoExtension.class)
class SuggestRejectConflictTest {

    @Mock
    WorkspaceAccessService access;

    @Mock
    SuggestSettingsRepository settingsRepository;

    @Mock
    SuggestItemRepository itemRepository;

    @Mock
    SuggestBulkJobRepository bulkJobRepository;

    @Mock
    ChannelRepository channelRepository;

    @Mock
    SocialAccountRepository socialAccountRepository;

    @Mock
    SuggestProcessingService processingService;

    @Mock
    SuggestSyncService syncService;

    @Mock
    SuggestBulkProcessor bulkProcessor;

    @Mock
    EventSubscriptionService eventSubscriptionService;

    @Mock
    AuditRecorder auditRecorder;

    @Mock
    FeatureFlagResolver featureFlagResolver;

    @Mock
    IdempotencyService idempotencyService;

    SuggestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SuggestServiceImpl(
                access,
                settingsRepository,
                itemRepository,
                bulkJobRepository,
                channelRepository,
                socialAccountRepository,
                processingService,
                syncService,
                bulkProcessor,
                eventSubscriptionService,
                auditRecorder,
                featureFlagResolver,
                idempotencyService);
    }

    @Test
    void publishedRejectIsConflict() {
        UUID userId = UuidV7.generate();
        UUID workspaceId = UuidV7.generate();
        UUID itemId = UuidV7.generate();
        UUID settingsId = UuidV7.generate();
        SuggestItemEntity item = new SuggestItemEntity();
        item.setId(itemId);
        item.setWorkspaceId(workspaceId);
        item.setSettingsId(settingsId);
        item.setStatus("published");
        SuggestSettingsEntity settings = new SuggestSettingsEntity();
        settings.setId(settingsId);
        settings.setWorkspaceId(workspaceId);
        settings.setProjectId(UuidV7.generate());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(settingsRepository.findById(settingsId)).thenReturn(Optional.of(settings));
        when(access.requirePermission(workspaceId, userId, "automations.moderate"))
                .thenReturn(new WorkspaceMembership(workspaceId, userId, RbacRole.owner, Set.of()));
        when(featureFlagResolver.resolveForWorkspace(workspaceId)).thenReturn(Map.of());
        when(processingService.reject(item, settings, userId, true))
                .thenThrow(new SuggestConflictException("Cannot reject a published post"));

        assertThatThrownBy(() -> service.reject(userId, itemId, new SuggestDtos.SuggestItemRejectRequest(true), null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }
}
