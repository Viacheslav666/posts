package ru.posts.api.automation.suggest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.admin.FeatureFlagResolver;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.idempotency.IdempotencyService;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.suggest.SuggestDtos;
import ru.posts.api.web.support.CursorPages;
import ru.posts.api.workspace.AuditRecorder;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.api.workspace.WorkspaceMembership;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.SocialAccountEntity;
import ru.posts.persistence.entity.SuggestBulkJobEntity;
import ru.posts.persistence.entity.SuggestItemEntity;
import ru.posts.persistence.entity.SuggestSettingsEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.SuggestBulkJobRepository;
import ru.posts.persistence.repository.SuggestItemRepository;
import ru.posts.persistence.repository.SuggestSettingsRepository;
import ru.posts.social.inbound.subscription.EventSubscriptionService;
import ru.posts.suggest.SuggestProcessingService;
import ru.posts.suggest.bulk.SuggestBulkProcessor;
import ru.posts.suggest.config.SuggestConfigSupport;
import ru.posts.suggest.sync.SuggestSyncService;

@Service
@Profile("!openapi")
public class SuggestServiceImpl implements SuggestService {

    public static final String FEATURE_FLAG = "automation.suggest";
    static final String IDEMPOTENCY_SCOPE_APPROVE = "POST /api/v1/suggest/items/approve";

    private final WorkspaceAccessService access;
    private final SuggestSettingsRepository settingsRepository;
    private final SuggestItemRepository itemRepository;
    private final SuggestBulkJobRepository bulkJobRepository;
    private final ChannelRepository channelRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final SuggestProcessingService processingService;
    private final SuggestSyncService syncService;
    private final SuggestBulkProcessor bulkProcessor;
    private final EventSubscriptionService eventSubscriptionService;
    private final AuditRecorder auditRecorder;
    private final FeatureFlagResolver featureFlagResolver;
    private final IdempotencyService idempotencyService;

    public SuggestServiceImpl(
            WorkspaceAccessService access,
            SuggestSettingsRepository settingsRepository,
            SuggestItemRepository itemRepository,
            SuggestBulkJobRepository bulkJobRepository,
            ChannelRepository channelRepository,
            SocialAccountRepository socialAccountRepository,
            SuggestProcessingService processingService,
            SuggestSyncService syncService,
            SuggestBulkProcessor bulkProcessor,
            EventSubscriptionService eventSubscriptionService,
            AuditRecorder auditRecorder,
            FeatureFlagResolver featureFlagResolver,
            IdempotencyService idempotencyService) {
        this.access = access;
        this.settingsRepository = settingsRepository;
        this.itemRepository = itemRepository;
        this.bulkJobRepository = bulkJobRepository;
        this.channelRepository = channelRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.processingService = processingService;
        this.syncService = syncService;
        this.bulkProcessor = bulkProcessor;
        this.eventSubscriptionService = eventSubscriptionService;
        this.auditRecorder = auditRecorder;
        this.featureFlagResolver = featureFlagResolver;
        this.idempotencyService = idempotencyService;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.SuggestSettingsPage listSettings(
            UUID userId, UUID workspaceId, UUID projectId, CursorPage.Params page) {
        WorkspaceMembership membership = access.requirePermission(workspaceId, userId, "automations.view");
        access.requireVisibleProject(membership, projectId);
        ensureFeatureEnabled(workspaceId);
        List<SuggestSettingsEntity> found =
                settingsRepository.findByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(workspaceId, projectId);
        CursorPages.Slice<SuggestSettingsEntity> slice = CursorPages.slice(
                found,
                page == null ? null : page.cursor(),
                page == null ? 20 : page.limitOrDefault(),
                SuggestSettingsEntity::getId);
        return new CursorPage.SuggestSettingsPage(
                slice.items().stream().map(this::toSettingsDto).toList(), slice.nextCursor(), slice.hasMore());
    }

    @Override
    @Transactional
    public SuggestDtos.SuggestSettings createSettings(
            UUID userId,
            UUID workspaceId,
            UUID projectId,
            SuggestDtos.SuggestSettingsCreate request,
            ClientRequestContext ctx) {
        WorkspaceMembership membership = access.requirePermission(workspaceId, userId, "automations.manage");
        access.requireVisibleProject(membership, projectId);
        ensureFeatureEnabled(workspaceId);
        ChannelEntity channel = requireChannel(workspaceId, request.channelId());
        if (!"vk".equalsIgnoreCase(channel.getNetwork())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.VALIDATION_FAILED, "VK channel required");
        }
        if (settingsRepository.findByChannelId(channel.getId()).isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Suggest settings already exist for channel");
        }
        Map<String, Object> config = SuggestConfigSupport.withDefaults(request.config());
        SuggestConfigSupport.validate(config);

        SuggestSettingsEntity entity = new SuggestSettingsEntity();
        entity.setId(UuidV7.generate());
        entity.setWorkspaceId(workspaceId);
        entity.setProjectId(projectId);
        entity.setChannelId(channel.getId());
        entity.setStatus("draft");
        entity.setMode(request.mode() == null ? "auto" : request.mode().name());
        entity.setConfig(config);
        entity.setConfigVersion(SuggestConfigSupport.CURRENT_CONFIG_VERSION);
        entity.setCreatedBy(userId);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        settingsRepository.save(entity);
        auditRecorder.record(
                workspaceId,
                userId,
                "suggest.settings.created",
                "suggest_settings",
                entity.getId(),
                Map.of("channelId", channel.getId().toString()),
                ctx == null ? null : ctx.ip());
        return toSettingsDto(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public SuggestDtos.SuggestSettings getSettings(UUID userId, UUID settingsId) {
        SuggestSettingsEntity settings = requireSettings(settingsId);
        access.requirePermission(settings.getWorkspaceId(), userId, "automations.view");
        ensureFeatureEnabled(settings.getWorkspaceId());
        return toSettingsDto(settings);
    }

    @Override
    @Transactional
    public SuggestDtos.SuggestSettings patchSettings(
            UUID userId, UUID settingsId, SuggestDtos.SuggestSettingsPatch request, ClientRequestContext ctx) {
        SuggestSettingsEntity settings = requireSettings(settingsId);
        WorkspaceMembership membership =
                access.requirePermission(settings.getWorkspaceId(), userId, "automations.manage");
        access.requireVisibleProject(membership, settings.getProjectId());
        ensureFeatureEnabled(settings.getWorkspaceId());
        if (request.version() != null && request.version() != settings.getVersion()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ProblemCodes.OPTIMISTIC_LOCK_CONFLICT, "Settings version conflict");
        }
        if (request.mode() != null) {
            settings.setMode(request.mode().name());
        }
        if (request.config() != null) {
            Map<String, Object> merged =
                    new LinkedHashMap<>(settings.getConfig() == null ? Map.of() : settings.getConfig());
            merged.putAll(request.config());
            Map<String, Object> config = SuggestConfigSupport.withDefaults(merged);
            SuggestConfigSupport.validate(config);
            settings.setConfig(config);
            settings.setConfigVersion(settings.getConfigVersion() + 1);
        }
        settings.setUpdatedAt(Instant.now());
        settingsRepository.save(settings);
        auditRecorder.record(
                settings.getWorkspaceId(),
                userId,
                "suggest.settings.updated",
                "suggest_settings",
                settings.getId(),
                Map.of("version", settings.getVersion()),
                ctx == null ? null : ctx.ip());
        return toSettingsDto(settings);
    }

    @Override
    @Transactional
    public SuggestDtos.SuggestSettings activate(UUID userId, UUID settingsId, ClientRequestContext ctx) {
        SuggestSettingsEntity settings = requireSettings(settingsId);
        WorkspaceMembership membership =
                access.requirePermission(settings.getWorkspaceId(), userId, "automations.manage");
        access.requireVisibleProject(membership, settings.getProjectId());
        ensureFeatureEnabled(settings.getWorkspaceId());
        ChannelEntity channel = requireChannel(settings.getWorkspaceId(), settings.getChannelId());
        requireWallScope(channel);

        eventSubscriptionService.ensure(channel.getId(), "suggest", List.of("wall_post_new"));

        Instant now = Instant.now();
        settings.setStatus("active");
        settings.setNextSyncAt(now.plusSeconds(600));
        settings.setUpdatedAt(now);
        settingsRepository.save(settings);
        auditRecorder.record(
                settings.getWorkspaceId(),
                userId,
                "suggest.settings.activated",
                "suggest_settings",
                settings.getId(),
                Map.of(),
                ctx == null ? null : ctx.ip());
        return toSettingsDto(settings);
    }

    @Override
    @Transactional
    public SuggestDtos.SuggestSettings pause(UUID userId, UUID settingsId, ClientRequestContext ctx) {
        SuggestSettingsEntity settings = requireSettings(settingsId);
        WorkspaceMembership membership =
                access.requirePermission(settings.getWorkspaceId(), userId, "automations.manage");
        access.requireVisibleProject(membership, settings.getProjectId());
        ensureFeatureEnabled(settings.getWorkspaceId());
        if (!"active".equals(settings.getStatus()) && !"auto_paused".equals(settings.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Only active settings can be paused");
        }
        settings.setStatus("paused");
        settings.setUpdatedAt(Instant.now());
        settingsRepository.save(settings);
        auditRecorder.record(
                settings.getWorkspaceId(),
                userId,
                "suggest.settings.paused",
                "suggest_settings",
                settings.getId(),
                Map.of(),
                ctx == null ? null : ctx.ip());
        return toSettingsDto(settings);
    }

    @Override
    @Transactional
    public SuggestDtos.SuggestSettings resume(UUID userId, UUID settingsId, ClientRequestContext ctx) {
        SuggestSettingsEntity settings = requireSettings(settingsId);
        WorkspaceMembership membership =
                access.requirePermission(settings.getWorkspaceId(), userId, "automations.manage");
        access.requireVisibleProject(membership, settings.getProjectId());
        ensureFeatureEnabled(settings.getWorkspaceId());
        if (!"paused".equals(settings.getStatus()) && !"auto_paused".equals(settings.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Only paused settings can be resumed");
        }
        ChannelEntity channel = requireChannel(settings.getWorkspaceId(), settings.getChannelId());
        requireWallScope(channel);
        Instant now = Instant.now();
        settings.setStatus("active");
        settings.setNextSyncAt(now.plusSeconds(600));
        settings.setUpdatedAt(now);
        settingsRepository.save(settings);
        auditRecorder.record(
                settings.getWorkspaceId(),
                userId,
                "suggest.settings.resumed",
                "suggest_settings",
                settings.getId(),
                Map.of(),
                ctx == null ? null : ctx.ip());
        return toSettingsDto(settings);
    }

    @Override
    @Transactional
    public SuggestDtos.SuggestSettingsCopyResult copy(
            UUID userId, UUID settingsId, SuggestDtos.SuggestSettingsCopyRequest request, ClientRequestContext ctx) {
        SuggestSettingsEntity source = requireSettings(settingsId);
        WorkspaceMembership membership =
                access.requirePermission(source.getWorkspaceId(), userId, "automations.manage");
        access.requireVisibleProject(membership, source.getProjectId());
        ensureFeatureEnabled(source.getWorkspaceId());

        List<SuggestDtos.SuggestSettingsCopyItem> results = new ArrayList<>();
        for (UUID channelId : request.channelIds()) {
            try {
                ChannelEntity channel = requireChannel(source.getWorkspaceId(), channelId);
                if (!"vk".equalsIgnoreCase(channel.getNetwork())) {
                    results.add(new SuggestDtos.SuggestSettingsCopyItem(channelId, false, "not_vk", null));
                    continue;
                }
                SuggestSettingsEntity existing =
                        settingsRepository.findByChannelId(channelId).orElse(null);
                Instant now = Instant.now();
                if (existing == null) {
                    existing = new SuggestSettingsEntity();
                    existing.setId(UuidV7.generate());
                    existing.setWorkspaceId(source.getWorkspaceId());
                    existing.setProjectId(source.getProjectId());
                    existing.setChannelId(channelId);
                    existing.setStatus("draft");
                    existing.setCreatedBy(userId);
                    existing.setCreatedAt(now);
                }
                existing.setMode(source.getMode());
                existing.setConfig(SuggestConfigSupport.withDefaults(source.getConfig()));
                existing.setConfigVersion(SuggestConfigSupport.CURRENT_CONFIG_VERSION);
                existing.setUpdatedAt(now);
                settingsRepository.save(existing);
                results.add(new SuggestDtos.SuggestSettingsCopyItem(channelId, true, null, existing.getId()));
            } catch (RuntimeException ex) {
                results.add(new SuggestDtos.SuggestSettingsCopyItem(channelId, false, ex.getMessage(), null));
            }
        }
        auditRecorder.record(
                source.getWorkspaceId(),
                userId,
                "suggest.settings.copied",
                "suggest_settings",
                source.getId(),
                Map.of("targets", request.channelIds().size()),
                ctx == null ? null : ctx.ip());
        return new SuggestDtos.SuggestSettingsCopyResult(results);
    }

    @Override
    @Transactional
    public SuggestDtos.SuggestSyncResult syncLast100(UUID userId, UUID settingsId, ClientRequestContext ctx) {
        SuggestSettingsEntity settings = requireSettings(settingsId);
        WorkspaceMembership membership =
                access.requirePermission(settings.getWorkspaceId(), userId, "automations.manage");
        access.requireVisibleProject(membership, settings.getProjectId());
        ensureFeatureEnabled(settings.getWorkspaceId());
        SuggestSyncService.SuggestSyncResult result = syncService.syncChannel(settingsId, 100);
        auditRecorder.record(
                settings.getWorkspaceId(),
                userId,
                "suggest.settings.synced",
                "suggest_settings",
                settings.getId(),
                Map.of("imported", result.imported()),
                ctx == null ? null : ctx.ip());
        return new SuggestDtos.SuggestSyncResult(
                result.imported(), result.removedExternally(), result.markedPublished(), result.status());
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.SuggestItemPage listItems(
            UUID userId,
            UUID settingsId,
            String status,
            String authorVkId,
            Instant from,
            Instant to,
            CursorPage.Params page) {
        SuggestSettingsEntity settings = requireSettings(settingsId);
        WorkspaceMembership membership =
                access.requirePermission(settings.getWorkspaceId(), userId, "automations.view");
        access.requireVisibleProject(membership, settings.getProjectId());
        ensureFeatureEnabled(settings.getWorkspaceId());

        Instant cursorCreatedAt = null;
        UUID cursorId = null;
        if (page != null && page.cursor() != null && !page.cursor().isBlank()) {
            // cursor format: createdAtEpochMillis:uuid
            String[] parts = page.cursor().split(":", 2);
            if (parts.length == 2) {
                cursorCreatedAt = Instant.ofEpochMilli(Long.parseLong(parts[0]));
                cursorId = UUID.fromString(parts[1]);
            } else {
                cursorId = UUID.fromString(page.cursor());
            }
        }
        int limit = page == null ? 20 : page.limitOrDefault();
        List<SuggestItemEntity> found = itemRepository.findByChannelCursor(
                settings.getChannelId(),
                status,
                authorVkId,
                from,
                to,
                cursorCreatedAt,
                cursorId,
                org.springframework.data.domain.PageRequest.of(0, limit + 1));
        boolean hasMore = found.size() > limit;
        List<SuggestItemEntity> pageItems = hasMore ? found.subList(0, limit) : found;
        String next = null;
        if (hasMore && !pageItems.isEmpty()) {
            SuggestItemEntity last = pageItems.get(pageItems.size() - 1);
            next = last.getCreatedAt().toEpochMilli() + ":" + last.getId();
        }
        return new CursorPage.SuggestItemPage(
                pageItems.stream().map(this::toItemDto).toList(), next, hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public SuggestDtos.SuggestItem getItem(UUID userId, UUID itemId) {
        SuggestItemEntity item = requireItem(itemId);
        access.requirePermission(item.getWorkspaceId(), userId, "automations.view");
        ensureFeatureEnabled(item.getWorkspaceId());
        return toItemDto(item);
    }

    @Override
    @Transactional
    public SuggestDtos.SuggestItem approve(
            UUID userId,
            UUID itemId,
            SuggestDtos.SuggestItemApproveRequest request,
            String idempotencyKey,
            ClientRequestContext ctx) {
        return idempotencyService.execute(
                userId,
                idempotencyKey,
                IDEMPOTENCY_SCOPE_APPROVE + "/" + itemId,
                request == null ? Map.of() : request,
                SuggestDtos.SuggestItem.class,
                () -> approveOnce(userId, itemId, request, ctx));
    }

    private SuggestDtos.SuggestItem approveOnce(
            UUID userId, UUID itemId, SuggestDtos.SuggestItemApproveRequest request, ClientRequestContext ctx) {
        SuggestItemEntity item = requireItem(itemId);
        SuggestSettingsEntity settings = requireSettings(item.getSettingsId());
        WorkspaceMembership membership =
                access.requirePermission(item.getWorkspaceId(), userId, "automations.moderate");
        access.requireVisibleProject(membership, settings.getProjectId());
        ensureFeatureEnabled(item.getWorkspaceId());
        if (!"pending_moderation".equals(item.getStatus()) && !"new".equals(item.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Item is not pending moderation");
        }
        SuggestItemEntity saved = processingService.approve(
                item,
                settings,
                userId,
                request == null ? null : request.message(),
                request == null ? null : request.attachments(),
                request == null ? null : request.scheduledFor());
        auditRecorder.record(
                item.getWorkspaceId(),
                userId,
                "suggest.item.approved",
                "suggest_item",
                item.getId(),
                Map.of("status", saved.getStatus()),
                ctx == null ? null : ctx.ip());
        return toItemDto(saved);
    }

    @Override
    @Transactional
    public SuggestDtos.SuggestItem reject(
            UUID userId, UUID itemId, SuggestDtos.SuggestItemRejectRequest request, ClientRequestContext ctx) {
        SuggestItemEntity item = requireItem(itemId);
        SuggestSettingsEntity settings = requireSettings(item.getSettingsId());
        WorkspaceMembership membership =
                access.requirePermission(item.getWorkspaceId(), userId, "automations.moderate");
        access.requireVisibleProject(membership, settings.getProjectId());
        ensureFeatureEnabled(item.getWorkspaceId());
        boolean deleteRemote =
                request == null || request.deleteRemote() == null || Boolean.TRUE.equals(request.deleteRemote());
        SuggestItemEntity saved = processingService.reject(item, settings, userId, deleteRemote);
        auditRecorder.record(
                item.getWorkspaceId(),
                userId,
                "suggest.item.rejected",
                "suggest_item",
                item.getId(),
                Map.of("deleteRemote", deleteRemote),
                ctx == null ? null : ctx.ip());
        return toItemDto(saved);
    }

    @Override
    @Transactional
    public SuggestDtos.SuggestBulkJob bulk(
            UUID userId,
            UUID settingsId,
            SuggestDtos.SuggestBulkRequest request,
            String idempotencyKey,
            ClientRequestContext ctx) {
        SuggestSettingsEntity settings = requireSettings(settingsId);
        WorkspaceMembership membership =
                access.requirePermission(settings.getWorkspaceId(), userId, "automations.moderate");
        access.requireVisibleProject(membership, settings.getProjectId());
        ensureFeatureEnabled(settings.getWorkspaceId());

        String key = idempotencyKey == null || idempotencyKey.isBlank() ? "bulk-" + UuidV7.generate() : idempotencyKey;
        var existing = bulkJobRepository.findByWorkspaceIdAndIdempotencyKey(settings.getWorkspaceId(), key);
        if (existing.isPresent()) {
            return toBulkDto(existing.get());
        }

        SuggestBulkJobEntity job = new SuggestBulkJobEntity();
        job.setId(UuidV7.generate());
        job.setWorkspaceId(settings.getWorkspaceId());
        job.setSettingsId(settings.getId());
        job.setChannelId(settings.getChannelId());
        job.setAction(request.action().name());
        job.setStatus("pending");
        job.setIdempotencyKey(key);
        job.setCreatedBy(userId);
        if (request.ids() != null) {
            job.setItemIds(request.ids().toArray(UUID[]::new));
        }
        Instant now = Instant.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        bulkJobRepository.save(job);

        SuggestBulkJobEntity processed = bulkProcessor.process(job.getId());
        auditRecorder.record(
                settings.getWorkspaceId(),
                userId,
                "suggest.bulk",
                "suggest_bulk_job",
                processed.getId(),
                Map.of("action", request.action().name(), "progress", progressPercent(processed)),
                ctx == null ? null : ctx.ip());
        return toBulkDto(processed);
    }

    @Override
    @Transactional(readOnly = true)
    public SuggestDtos.SuggestBulkJob getBulkJob(UUID userId, UUID jobId) {
        SuggestBulkJobEntity job = bulkJobRepository
                .findById(jobId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Bulk job not found"));
        access.requirePermission(job.getWorkspaceId(), userId, "automations.moderate");
        ensureFeatureEnabled(job.getWorkspaceId());
        // IDOR: findByIdAndWorkspaceId after permission — also verify membership owns workspace
        return toBulkDto(job);
    }

    private void ensureFeatureEnabled(UUID workspaceId) {
        Boolean enabled = featureFlagResolver.resolveForWorkspace(workspaceId).get(FEATURE_FLAG);
        // Missing flag → treat as enabled (seeded by V28 when present). Explicit false → 422.
        if (Boolean.FALSE.equals(enabled)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.SUGGEST_FEATURE_DISABLED,
                    "Suggest automation is disabled for this workspace");
        }
    }

    private void requireWallScope(ChannelEntity channel) {
        boolean capabilityOk = false;
        Map<String, Object> caps = channel.getCapabilities();
        if (caps != null) {
            Object pub = caps.get("publishSuggested");
            if (Boolean.TRUE.equals(pub) || "true".equalsIgnoreCase(String.valueOf(pub))) {
                capabilityOk = true;
            }
            Object moderation = caps.get("moderation");
            if (moderation instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("publishSuggested"))) {
                capabilityOk = true;
            }
        }
        boolean scopeOk = false;
        if (channel.getSocialAccountId() != null) {
            SocialAccountEntity account = socialAccountRepository
                    .findById(channel.getSocialAccountId())
                    .orElse(null);
            if (account != null && account.getScopes() != null) {
                for (String scope : account.getScopes()) {
                    if (scope != null && scope.toLowerCase(Locale.ROOT).contains("wall")) {
                        scopeOk = true;
                        break;
                    }
                }
            }
        }
        if (!capabilityOk && !scopeOk) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.VK_USER_TOKEN_WALL_SCOPE_REQUIRED,
                    "VK user token with wall scope is required to publish suggested posts");
        }
    }

    private SuggestSettingsEntity requireSettings(UUID settingsId) {
        return settingsRepository
                .findById(settingsId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Not found"));
    }

    private SuggestItemEntity requireItem(UUID itemId) {
        return itemRepository
                .findById(itemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Not found"));
    }

    private ChannelEntity requireChannel(UUID workspaceId, UUID channelId) {
        ChannelEntity channel = channelRepository
                .findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found"));
        if (!channel.getWorkspaceId().equals(workspaceId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found");
        }
        return channel;
    }

    private SuggestDtos.SuggestSettings toSettingsDto(SuggestSettingsEntity e) {
        return new SuggestDtos.SuggestSettings(
                e.getId(),
                e.getWorkspaceId(),
                e.getProjectId(),
                e.getChannelId(),
                SuggestDtos.SettingsStatus.valueOf(e.getStatus()),
                SuggestDtos.Mode.valueOf(e.getMode()),
                e.getConfig(),
                e.getConfigVersion(),
                e.getVersion(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getLastSyncAt(),
                e.getNextSyncAt());
    }

    private SuggestDtos.SuggestItem toItemDto(SuggestItemEntity e) {
        List<String> reasons = e.getReasons() == null ? List.of() : Arrays.asList(e.getReasons());
        return new SuggestDtos.SuggestItem(
                e.getId(),
                e.getWorkspaceId(),
                e.getChannelId(),
                e.getSettingsId(),
                e.getVkPostId(),
                e.getAuthorVkId(),
                e.getText(),
                e.getAttachments(),
                e.getStatus(),
                reasons,
                e.getDecidedBy(),
                e.getDecidedAt(),
                e.getScheduledFor(),
                e.getRemoteActionId(),
                e.getPublishedRemotePostId(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private SuggestDtos.SuggestBulkJob toBulkDto(SuggestBulkJobEntity e) {
        return new SuggestDtos.SuggestBulkJob(
                e.getId(),
                e.getWorkspaceId(),
                e.getSettingsId(),
                e.getChannelId(),
                e.getAction(),
                e.getStatus(),
                e.getTotal(),
                e.getProcessed(),
                e.getSucceeded(),
                e.getFailed(),
                progressPercent(e),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getFinishedAt());
    }

    private static int progressPercent(SuggestBulkJobEntity e) {
        if (e.getTotal() <= 0) {
            return "succeeded".equals(e.getStatus()) ? 100 : 0;
        }
        return Math.min(100, (int) Math.round(100.0 * e.getProcessed() / e.getTotal()));
    }
}
