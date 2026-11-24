package ru.posts.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import ru.posts.api.config.S3Properties;
import ru.posts.api.idempotency.IdempotencyService;
import ru.posts.api.web.dto.media.MediaDtos;
import ru.posts.api.workspace.AuditRecorder;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.repository.MediaAssetRepository;
import ru.posts.persistence.repository.PlanRepository;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class MediaServiceFoldersQuotaTest {

    private final MediaAssetRepository media = mock(MediaAssetRepository.class);
    private final PlanRepository plans = mock(PlanRepository.class);
    private final WorkspaceAccessService access = mock(WorkspaceAccessService.class);
    private MediaServiceImpl service;

    private final UUID userId = UUID.fromString("019a0000-0001-7000-8000-000000000001");
    private final UUID workspaceId = UUID.fromString("019a0000-0002-7000-8000-000000000002");
    private final UUID planId = UUID.fromString("019a0000-0003-7000-8000-000000000003");

    @BeforeEach
    void setUp() {
        service = new MediaServiceImpl(
                media,
                plans,
                access,
                mock(AuditRecorder.class),
                mock(S3Client.class),
                mock(S3Presigner.class),
                new S3Properties(),
                mock(ObjectProvider.class),
                mock(IdempotencyService.class),
                mock(PlatformTransactionManager.class),
                mock(DataSource.class));
    }

    @Test
    void quotaReusesStorageLimitAndCountsAssets() {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setId(workspaceId);
        workspace.setPlanId(planId);
        PlanEntity plan = new PlanEntity();
        plan.setId(planId);
        plan.setLimits(Map.of("storage_bytes", 2_000L, "media_count", 25));
        when(access.requireWorkspace(workspaceId)).thenReturn(workspace);
        when(plans.findById(planId)).thenReturn(Optional.of(plan));
        when(media.sumSizeBytesByWorkspaceId(workspaceId)).thenReturn(400L);
        when(media.countByWorkspaceIdAndDeletedAtIsNull(workspaceId)).thenReturn(3L);

        MediaDtos.MediaQuota quota = service.quota(userId, workspaceId);

        verify(access).requirePermission(workspaceId, userId, "media.view");
        assertThat(quota.usedBytes()).isEqualTo(400L);
        assertThat(quota.limitBytes()).isEqualTo(2_000L);
        assertThat(quota.usedCount()).isEqualTo(3L);
        assertThat(quota.limitCount()).isEqualTo(25L);
    }

    @Test
    void foldersAreDistinctNamesForTheWorkspace() {
        when(media.findDistinctFoldersByWorkspaceId(workspaceId)).thenReturn(List.of("Кампании", "Истории"));

        MediaDtos.MediaFolderList folders = service.listFolders(userId, workspaceId);

        verify(access).requirePermission(workspaceId, userId, "media.view");
        assertThat(folders.items()).extracting(MediaDtos.MediaFolder::name).containsExactly("Кампании", "Истории");
        assertThat(folders.items()).allMatch(folder -> folder.workspaceId().equals(workspaceId));
        assertThat(folders.items().get(0).id()).isEqualTo("Кампании");
    }
}
