package ru.posts.api.ingest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.idempotency.IdempotencyService;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.ingest.IngestDtos;
import ru.posts.api.web.support.CursorPages;
import ru.posts.api.workspace.AuditRecorder;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.api.workspace.WorkspaceMembership;
import ru.posts.common.uuid.UuidV7;
import ru.posts.grabber.GrabberRunProcessor;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.config.GrabberProperties;
import ru.posts.grabber.config.SourceIdNormalizer;
import ru.posts.grabber.fetch.RssFetcher;
import ru.posts.grabber.fetch.SourceCandidate;
import ru.posts.grabber.fetch.VkWallFetcher;
import ru.posts.grabber.fetch.WebsiteFetcher;
import ru.posts.grabber.hash.ContentHasher;
import ru.posts.grabber.publish.GrabberPostFactory;
import ru.posts.grabber.ssrf.SsrfBlockedException;
import ru.posts.grabber.ssrf.SsrfGuard;
import ru.posts.grabber.ssrf.SsrfProperties;
import ru.posts.grabber.token.GrabberTokenProvider;
import ru.posts.messaging.ingest.GrabberRunCommand;
import ru.posts.messaging.topology.IngestTopology;
import ru.posts.persistence.entity.AutomationRuleEntity;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.GrabberItemEntity;
import ru.posts.persistence.entity.GrabberRunEntity;
import ru.posts.persistence.repository.AutomationRuleRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.GrabberItemRepository;
import ru.posts.persistence.repository.GrabberRunRepository;

@Service
@Profile("!openapi")
@Transactional
public class IngestServiceImpl implements IngestService {

    static final String IDEMPOTENCY_SCOPE_RULE = "POST /api/v1/ingest/rules";
    static final String IDEMPOTENCY_SCOPE_RUN = "POST /api/v1/ingest/rules/runs";
    static final String IDEMPOTENCY_SCOPE_APPROVE = "POST /api/v1/ingest/items/approve";

    private final AutomationRuleRepository ruleRepository;
    private final GrabberRunRepository runRepository;
    private final GrabberItemRepository itemRepository;
    private final ChannelRepository channelRepository;
    private final WorkspaceAccessService access;
    private final AuditRecorder auditRecorder;
    private final IdempotencyService idempotencyService;
    private final GrabberRunProcessor runProcessor;
    private final GrabberPostFactory postFactory;
    private final RssFetcher rssFetcher;
    private final WebsiteFetcher websiteFetcher;
    private final VkWallFetcher vkWallFetcher;
    private final GrabberTokenProvider tokenProvider;
    private final ObjectProvider<RabbitTemplate> rabbitTemplate;
    private final SsrfProperties ssrfProperties;
    private final GrabberProperties grabberProperties;
    private final boolean inlineRuns;

    public IngestServiceImpl(
            AutomationRuleRepository ruleRepository,
            GrabberRunRepository runRepository,
            GrabberItemRepository itemRepository,
            ChannelRepository channelRepository,
            WorkspaceAccessService access,
            AuditRecorder auditRecorder,
            IdempotencyService idempotencyService,
            GrabberRunProcessor runProcessor,
            GrabberPostFactory postFactory,
            RssFetcher rssFetcher,
            WebsiteFetcher websiteFetcher,
            VkWallFetcher vkWallFetcher,
            GrabberTokenProvider tokenProvider,
            ObjectProvider<RabbitTemplate> rabbitTemplate,
            SsrfProperties ssrfProperties,
            GrabberProperties grabberProperties,
            @org.springframework.beans.factory.annotation.Value("${posts.grabber.inline-runs:true}")
                    boolean inlineRuns) {
        this.ruleRepository = ruleRepository;
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.channelRepository = channelRepository;
        this.access = access;
        this.auditRecorder = auditRecorder;
        this.idempotencyService = idempotencyService;
        this.runProcessor = runProcessor;
        this.postFactory = postFactory;
        this.rssFetcher = rssFetcher;
        this.websiteFetcher = websiteFetcher;
        this.vkWallFetcher = vkWallFetcher;
        this.tokenProvider = tokenProvider;
        this.rabbitTemplate = rabbitTemplate;
        this.ssrfProperties = ssrfProperties;
        this.grabberProperties = grabberProperties;
        this.inlineRuns = inlineRuns;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.GrabberRulePage listRules(
            UUID userId, UUID workspaceId, UUID projectId, IngestDtos.RuleType type, CursorPage.Params page) {
        WorkspaceMembership membership = access.requirePermission(workspaceId, userId, "automations.view");
        access.requireVisibleProject(membership, projectId);
        List<AutomationRuleEntity> found = type == null
                ? ruleRepository.findByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(workspaceId, projectId)
                : ruleRepository.findByWorkspaceIdAndProjectIdAndTypeInOrderByCreatedAtDesc(
                        workspaceId, projectId, List.of(type.name()));
        int limit = page == null ? 20 : page.limitOrDefault();
        String cursor = page == null ? null : page.cursor();
        CursorPages.Slice<AutomationRuleEntity> slice =
                CursorPages.slice(found, cursor, limit, AutomationRuleEntity::getId);
        List<IngestDtos.GrabberRule> items =
                slice.items().stream().map(this::toRuleDto).toList();
        return new CursorPage.GrabberRulePage(items, slice.nextCursor(), slice.hasMore());
    }

    @Override
    public IngestDtos.GrabberRule createRule(
            UUID userId,
            UUID workspaceId,
            UUID projectId,
            IngestDtos.GrabberRuleCreate request,
            String idempotencyKey,
            ClientRequestContext ctx) {
        return idempotencyService.execute(
                userId,
                idempotencyKey,
                IDEMPOTENCY_SCOPE_RULE,
                request,
                IngestDtos.GrabberRule.class,
                () -> createRuleOnce(userId, workspaceId, projectId, request, ctx));
    }

    private IngestDtos.GrabberRule createRuleOnce(
            UUID userId,
            UUID workspaceId,
            UUID projectId,
            IngestDtos.GrabberRuleCreate request,
            ClientRequestContext ctx) {
        requireWebsiteEnabled(request.type());
        WorkspaceMembership membership = access.requirePermission(workspaceId, userId, "automations.manage");
        access.requireVisibleProject(membership, projectId);
        ChannelEntity channel = requireChannel(workspaceId, request.targetChannelId());
        Map<String, Object> config = buildConfig(request.type(), request.config());
        validateConfig(request.type(), config);

        Instant now = Instant.now();
        AutomationRuleEntity rule = new AutomationRuleEntity();
        rule.setId(UuidV7.generate());
        rule.setWorkspaceId(workspaceId);
        rule.setProjectId(projectId);
        rule.setType(request.type().name());
        rule.setName(request.name().trim());
        rule.setTargetChannelId(channel.getId());
        rule.setConfig(config);
        rule.setStatus("draft");
        rule.setCreatedBy(userId);
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        if (Boolean.TRUE.equals(request.userResponsibilityAccepted())) {
            rule.setUserResponsibilityAcceptedAt(now);
            rule.setUserResponsibilityAcceptedBy(userId);
        }
        ruleRepository.save(rule);

        if (Boolean.TRUE.equals(request.activate())) {
            activateInternal(rule, userId, now);
        }

        auditRecorder.record(
                workspaceId,
                userId,
                "ingest.rule.created",
                "ingest_rule",
                rule.getId(),
                Map.of(
                        "type",
                        rule.getType(),
                        "targetChannelId",
                        channel.getId().toString()),
                ctx == null ? null : ctx.ip());
        return toRuleDto(rule);
    }

    @Override
    @Transactional(readOnly = true)
    public IngestDtos.GrabberRule getRule(UUID userId, UUID ruleId) {
        AutomationRuleEntity rule = requireRule(ruleId);
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.view");
        access.requireVisibleProject(membership, rule.getProjectId());
        return toRuleDto(rule);
    }

    @Override
    public IngestDtos.GrabberRule patchRule(
            UUID userId, UUID ruleId, IngestDtos.GrabberRulePatch request, ClientRequestContext ctx) {
        AutomationRuleEntity rule = requireRule(ruleId);
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.manage");
        access.requireVisibleProject(membership, rule.getProjectId());
        if ("archived".equals(rule.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Rule is archived");
        }
        if (request.version() == null || request.version() != rule.getVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.OPTIMISTIC_LOCK_CONFLICT, "Rule version mismatch");
        }
        if (request.name() != null) {
            rule.setName(request.name().trim());
        }
        if (request.targetChannelId() != null) {
            ChannelEntity channel = requireChannel(rule.getWorkspaceId(), request.targetChannelId());
            rule.setTargetChannelId(channel.getId());
        }
        if (request.config() != null) {
            Map<String, Object> config = buildConfig(IngestDtos.RuleType.valueOf(rule.getType()), request.config());
            validateConfig(IngestDtos.RuleType.valueOf(rule.getType()), config);
            rule.setConfig(config);
            rule.setConfigVersion(rule.getConfigVersion() + 1);
        }
        rule.setUpdatedAt(Instant.now());
        ruleRepository.save(rule);
        auditRecorder.record(
                rule.getWorkspaceId(),
                userId,
                "ingest.rule.updated",
                "ingest_rule",
                rule.getId(),
                Map.of("version", rule.getVersion()),
                ctx == null ? null : ctx.ip());
        return toRuleDto(rule);
    }

    @Override
    public IngestDtos.GrabberRule activate(UUID userId, UUID ruleId, ClientRequestContext ctx) {
        AutomationRuleEntity rule = requireRule(ruleId);
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.manage");
        access.requireVisibleProject(membership, rule.getProjectId());
        activateInternal(rule, userId, Instant.now());
        auditRecorder.record(
                rule.getWorkspaceId(),
                userId,
                "ingest.rule.activated",
                "ingest_rule",
                rule.getId(),
                Map.of("acceptedBy", userId.toString()),
                ctx == null ? null : ctx.ip());
        return toRuleDto(rule);
    }

    private void activateInternal(AutomationRuleEntity rule, UUID userId, Instant now) {
        if (rule.getUserResponsibilityAcceptedAt() == null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.INGEST_RESPONSIBILITY_REQUIRED,
                    "User responsibility checkbox is required");
        }
        if (!GrabberConfigSupport.attributionEnabled(rule.getConfig())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.INGEST_ATTRIBUTION_REQUIRED,
                    "Attribution must be enabled");
        }
        String template = GrabberConfigSupport.attributionTemplate(rule.getConfig());
        if (template == null || template.isBlank()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.INGEST_ATTRIBUTION_REQUIRED,
                    "Attribution template is required");
        }
        requireChannel(rule.getWorkspaceId(), rule.getTargetChannelId());
        rule.setStatus("active");
        int interval = GrabberConfigSupport.scanIntervalMinutes(rule.getConfig());
        rule.setNextRunAt(now.plus(interval, ChronoUnit.MINUTES));
        rule.setUpdatedAt(now);
        if (rule.getUserResponsibilityAcceptedBy() == null) {
            rule.setUserResponsibilityAcceptedBy(userId);
            rule.setUserResponsibilityAcceptedAt(now);
        }
        ruleRepository.save(rule);
    }

    @Override
    public IngestDtos.GrabberRule pause(UUID userId, UUID ruleId, ClientRequestContext ctx) {
        AutomationRuleEntity rule = requireRule(ruleId);
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.manage");
        access.requireVisibleProject(membership, rule.getProjectId());
        if (!"active".equals(rule.getStatus()) && !"auto_paused".equals(rule.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Only active rules can be paused");
        }
        rule.setStatus("paused");
        rule.setUpdatedAt(Instant.now());
        ruleRepository.save(rule);
        auditRecorder.record(
                rule.getWorkspaceId(),
                userId,
                "ingest.rule.paused",
                "ingest_rule",
                rule.getId(),
                Map.of("reason", "user"),
                ctx == null ? null : ctx.ip());
        return toRuleDto(rule);
    }

    @Override
    public IngestDtos.GrabberRule resume(UUID userId, UUID ruleId, ClientRequestContext ctx) {
        AutomationRuleEntity rule = requireRule(ruleId);
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.manage");
        access.requireVisibleProject(membership, rule.getProjectId());
        if (!"paused".equals(rule.getStatus()) && !"auto_paused".equals(rule.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Only paused rules can be resumed");
        }
        requireChannel(rule.getWorkspaceId(), rule.getTargetChannelId());
        Instant now = Instant.now();
        rule.setStatus("active");
        rule.setConsecutiveFailures(0);
        int interval = GrabberConfigSupport.scanIntervalMinutes(rule.getConfig());
        rule.setNextRunAt(now.plus(interval, ChronoUnit.MINUTES));
        rule.setUpdatedAt(now);
        ruleRepository.save(rule);
        auditRecorder.record(
                rule.getWorkspaceId(),
                userId,
                "ingest.rule.resumed",
                "ingest_rule",
                rule.getId(),
                Map.of(),
                ctx == null ? null : ctx.ip());
        return toRuleDto(rule);
    }

    @Override
    public IngestDtos.GrabberRule archive(UUID userId, UUID ruleId, ClientRequestContext ctx) {
        AutomationRuleEntity rule = requireRule(ruleId);
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.manage");
        access.requireVisibleProject(membership, rule.getProjectId());
        rule.setStatus("archived");
        rule.setUpdatedAt(Instant.now());
        ruleRepository.save(rule);
        auditRecorder.record(
                rule.getWorkspaceId(),
                userId,
                "ingest.rule.archived",
                "ingest_rule",
                rule.getId(),
                Map.of(),
                ctx == null ? null : ctx.ip());
        return toRuleDto(rule);
    }

    @Override
    public IngestDtos.GrabberRun startRun(UUID userId, UUID ruleId, String idempotencyKey, ClientRequestContext ctx) {
        return idempotencyService.execute(
                userId,
                idempotencyKey,
                IDEMPOTENCY_SCOPE_RUN + "/" + ruleId,
                Map.of("ruleId", ruleId.toString()),
                IngestDtos.GrabberRun.class,
                () -> startRunOnce(userId, ruleId, ctx));
    }

    private IngestDtos.GrabberRun startRunOnce(UUID userId, UUID ruleId, ClientRequestContext ctx) {
        AutomationRuleEntity rule = requireRule(ruleId);
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.manage");
        access.requireVisibleProject(membership, rule.getProjectId());
        if (!"active".equals(rule.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Only active rules can be run manually");
        }
        Instant since = Instant.now().minus(5, ChronoUnit.MINUTES);
        if (runRepository.countByRuleIdAndTriggerAndStartedAtAfter(ruleId, "manual", since) > 0) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    ProblemCodes.INGEST_RATE_LIMITED,
                    "Manual run rate limit: at most once per 5 minutes");
        }

        GrabberRunEntity run = new GrabberRunEntity();
        run.setId(UuidV7.generate());
        run.setRuleId(rule.getId());
        run.setWorkspaceId(rule.getWorkspaceId());
        run.setTrigger("manual");
        run.setStatus("running");
        run.setStartedAt(Instant.now());
        run.setTraceId(UuidV7.generate().toString());
        runRepository.save(run);

        if (inlineRuns || rabbitTemplate.getIfAvailable() == null) {
            run = runProcessor.process(rule.getId(), run.getId(), "manual", run.getTraceId());
        } else {
            rabbitTemplate
                    .getObject()
                    .convertAndSend(
                            IngestTopology.INGEST_EXCHANGE,
                            IngestTopology.GRABBER_ROUTING_KEY,
                            new GrabberRunCommand(rule.getId(), run.getId(), "manual", run.getTraceId()));
        }
        auditRecorder.record(
                rule.getWorkspaceId(),
                userId,
                "ingest.run.started",
                "ingest_run",
                run.getId(),
                Map.of("trigger", "manual"),
                ctx == null ? null : ctx.ip());
        return toRunDto(runRepository.findById(run.getId()).orElse(run));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.GrabberRunPage listRuns(UUID userId, UUID ruleId, CursorPage.Params page) {
        AutomationRuleEntity rule = requireRule(ruleId);
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.view");
        access.requireVisibleProject(membership, rule.getProjectId());
        List<GrabberRunEntity> found = runRepository.findByRuleIdOrderByStartedAtDesc(ruleId);
        int limit = page == null ? 20 : page.limitOrDefault();
        CursorPages.Slice<GrabberRunEntity> slice =
                CursorPages.slice(found, page == null ? null : page.cursor(), limit, GrabberRunEntity::getId);
        return new CursorPage.GrabberRunPage(
                slice.items().stream().map(this::toRunDto).toList(), slice.nextCursor(), slice.hasMore());
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.GrabberItemPage listItems(
            UUID userId, UUID ruleId, IngestDtos.ItemStatus status, CursorPage.Params page) {
        AutomationRuleEntity rule = requireRule(ruleId);
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.view");
        access.requireVisibleProject(membership, rule.getProjectId());
        List<GrabberItemEntity> found = status == null
                ? itemRepository.findByRuleIdOrderByDetectedAtDesc(ruleId)
                : itemRepository.findByRuleIdAndStatusOrderByDetectedAtDesc(ruleId, status.name());
        int limit = page == null ? 20 : page.limitOrDefault();
        CursorPages.Slice<GrabberItemEntity> slice =
                CursorPages.slice(found, page == null ? null : page.cursor(), limit, GrabberItemEntity::getId);
        return new CursorPage.GrabberItemPage(
                slice.items().stream().map(this::toItemDto).toList(), slice.nextCursor(), slice.hasMore());
    }

    @Override
    @Transactional(readOnly = true)
    public IngestDtos.GrabberItem getItem(UUID userId, UUID itemId) {
        GrabberItemEntity item = requireItem(itemId);
        AutomationRuleEntity rule = requireRule(item.getRuleId());
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.view");
        access.requireVisibleProject(membership, rule.getProjectId());
        return toItemDto(item);
    }

    @Override
    public IngestDtos.GrabberItem approve(
            UUID userId,
            UUID itemId,
            IngestDtos.GrabberItemApproveRequest request,
            String idempotencyKey,
            ClientRequestContext ctx) {
        return idempotencyService.execute(
                userId,
                idempotencyKey,
                IDEMPOTENCY_SCOPE_APPROVE + "/" + itemId,
                request == null ? Map.of() : request,
                IngestDtos.GrabberItem.class,
                () -> approveOnce(userId, itemId, request == null ? null : request.scheduledAt(), null, ctx, false));
    }

    @Override
    public IngestDtos.GrabberItem reject(
            UUID userId, UUID itemId, IngestDtos.GrabberItemRejectRequest request, ClientRequestContext ctx) {
        GrabberItemEntity item = requireItem(itemId);
        AutomationRuleEntity rule = requireRule(item.getRuleId());
        WorkspaceMembership membership =
                access.requirePermission(rule.getWorkspaceId(), userId, "automations.moderate");
        access.requireVisibleProject(membership, rule.getProjectId());
        if (!"pending_moderation".equals(item.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Item is not pending moderation");
        }
        Instant now = Instant.now();
        item.setStatus("rejected");
        item.setSkipReason(request == null ? null : request.reason());
        item.setModeratedBy(userId);
        item.setModeratedAt(now);
        item.setUpdatedAt(now);
        itemRepository.save(item);
        auditRecorder.record(
                rule.getWorkspaceId(),
                userId,
                "ingest.item.rejected",
                "ingest_item",
                item.getId(),
                Map.of("reason", request == null || request.reason() == null ? "" : request.reason()),
                ctx == null ? null : ctx.ip());
        return toItemDto(item);
    }

    @Override
    public IngestDtos.GrabberItem editAndApprove(
            UUID userId,
            UUID itemId,
            IngestDtos.GrabberItemEditApproveRequest request,
            String idempotencyKey,
            ClientRequestContext ctx) {
        return idempotencyService.execute(
                userId,
                idempotencyKey,
                IDEMPOTENCY_SCOPE_APPROVE + "/edit/" + itemId,
                request,
                IngestDtos.GrabberItem.class,
                () -> approveOnce(userId, itemId, request.scheduledAt(), request.contentText(), ctx, true));
    }

    private IngestDtos.GrabberItem approveOnce(
            UUID userId,
            UUID itemId,
            Instant scheduledAt,
            String overrideText,
            ClientRequestContext ctx,
            boolean edited) {
        GrabberItemEntity item = requireItem(itemId);
        AutomationRuleEntity rule = requireRule(item.getRuleId());
        WorkspaceMembership membership =
                access.requirePermission(rule.getWorkspaceId(), userId, "automations.moderate");
        access.requireVisibleProject(membership, rule.getProjectId());
        access.requireAction(
                membership, "posts.schedule", ru.posts.security.rbac.RbacEvaluationContext.workspaceOnly());

        if (item.getPostId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Item already approved/published");
        }
        if (!"pending_moderation".equals(item.getStatus()) && !"approved".equals(item.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Item is not approvable");
        }

        Instant now = Instant.now();
        if (overrideText != null) {
            item.setEditedText(overrideText);
            item.setContentText(overrideText);
        }
        GrabberPostFactory.PublishResult published =
                postFactory.publishItem(rule, item, userId, overrideText, scheduledAt == null ? now : scheduledAt);
        item.setModeratedBy(userId);
        item.setModeratedAt(now);
        item.setStatus(published.postId() == null ? item.getStatus() : "scheduled");
        item.setUpdatedAt(now);
        itemRepository.save(item);

        auditRecorder.record(
                rule.getWorkspaceId(),
                userId,
                edited ? "ingest.item.edit_approved" : "ingest.item.approved",
                "ingest_item",
                item.getId(),
                Map.of(
                        "postId",
                        published.postId() == null ? "" : published.postId().toString()),
                ctx == null ? null : ctx.ip());
        return toItemDto(item);
    }

    @Override
    public IngestDtos.GrabberBulkModerationResponse bulk(
            UUID userId, IngestDtos.GrabberBulkModerationRequest request, ClientRequestContext ctx) {
        GrabberItemEntity first = requireItem(request.itemIds().getFirst());
        AutomationRuleEntity firstRule = requireRule(first.getRuleId());
        WorkspaceMembership membership =
                access.requirePermission(firstRule.getWorkspaceId(), userId, "automations.moderate");
        access.requireVisibleProject(membership, firstRule.getProjectId());

        int ok = 0;
        List<UUID> failed = new ArrayList<>();
        for (UUID itemId : request.itemIds()) {
            try {
                if (request.action() == IngestDtos.BulkAction.approve) {
                    approveOnce(userId, itemId, request.scheduledAt(), null, ctx, false);
                } else {
                    reject(userId, itemId, new IngestDtos.GrabberItemRejectRequest(request.reason()), ctx);
                }
                ok++;
            } catch (RuntimeException ex) {
                failed.add(itemId);
            }
        }
        auditRecorder.record(
                first.getWorkspaceId(),
                userId,
                "ingest.bulk_moderated",
                "ingest_item",
                first.getId(),
                Map.of(
                        "action",
                        request.action().name(),
                        "count",
                        request.itemIds().size(),
                        "succeeded",
                        ok),
                ctx == null ? null : ctx.ip());
        return new IngestDtos.GrabberBulkModerationResponse(ok, failed.size(), failed);
    }

    @Override
    @Transactional(readOnly = true)
    public IngestDtos.IngestPreviewResponse preview(
            UUID userId, UUID workspaceId, IngestDtos.IngestPreviewRequest request) {
        access.requirePermission(workspaceId, userId, "automations.view");
        requireWebsiteEnabled(request.type());
        int limit = request.limit() == null ? 5 : Math.min(20, Math.max(1, request.limit()));
        List<SourceCandidate> candidates;
        try {
            if (request.type() == IngestDtos.RuleType.rss) {
                SsrfGuard.validateHttpUrl(request.rssUrl(), ssrfProperties.getAllowedHosts());
                candidates = rssFetcher.fetch(request.rssUrl(), limit);
            } else if (request.type() == IngestDtos.RuleType.website) {
                websiteFetcher.validateUrls(request.websiteUrls());
                candidates = websiteFetcher.fetch(request.websiteUrls(), request.websiteSelector());
            } else {
                List<String> ids = SourceIdNormalizer.requireValid(request.sourceIds(), 50);
                String token = tokenProvider
                        .resolveVkToken(workspaceId, null)
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                ProblemCodes.INGEST_SOURCE_UNREACHABLE,
                                "No VK token"));
                candidates = new ArrayList<>();
                for (String id : ids) {
                    candidates.addAll(vkWallFetcher.fetch(id, token, limit));
                }
            }
        } catch (SsrfBlockedException ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.INGEST_SSRF_BLOCKED, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.VALIDATION_FAILED, ex.getMessage());
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.INGEST_SOURCE_UNREACHABLE,
                    ex.getMessage() == null ? "Source unreachable" : ex.getMessage());
        }
        List<IngestDtos.GrabberItem> items = candidates.stream()
                .limit(limit)
                .map(c -> new IngestDtos.GrabberItem(
                        null,
                        null,
                        null,
                        workspaceId,
                        c.sourceNetwork(),
                        c.sourceId(),
                        c.remotePostId(),
                        c.sourcePermalink(),
                        c.sourceName(),
                        c.contentText(),
                        ContentHasher.hash(c.contentText(), c.mediaHashes(), c.attachmentCanonicalIds()),
                        IngestDtos.ItemStatus.discovered,
                        null,
                        null,
                        Instant.now(),
                        null,
                        null))
                .toList();
        return new IngestDtos.IngestPreviewResponse(items);
    }

    private Map<String, Object> buildConfig(IngestDtos.RuleType type, IngestDtos.GrabberConfig dto) {
        Map<String, Object> raw = new LinkedHashMap<>();
        if (dto != null) {
            if (dto.sourceIds() != null) {
                raw.put("source_ids", dto.sourceIds());
            }
            if (dto.rssUrl() != null) {
                raw.put("rss_url", dto.rssUrl());
            }
            if (dto.websiteUrls() != null) {
                raw.put("website_urls", dto.websiteUrls());
            }
            if (dto.websiteSelector() != null) {
                raw.put("website_selector", dto.websiteSelector());
            }
            if (dto.scanIntervalMinutes() != null) {
                raw.put("scan_interval_minutes", dto.scanIntervalMinutes());
            }
            if (dto.publishMode() != null) {
                raw.put("publish_mode", dto.publishMode().name());
            }
            if (dto.scheduleMode() != null) {
                raw.put("schedule_mode", dto.scheduleMode());
            }
            if (dto.publishDelayMinutes() != null) {
                raw.put("publish_delay_minutes", dto.publishDelayMinutes());
            }
            if (dto.filters() != null) {
                raw.put("filters", dto.filters());
            }
            if (dto.transform() != null) {
                raw.put("transform", dto.transform());
            }
            if (dto.attribution() != null) {
                raw.put("attribution", dto.attribution());
            }
            if (dto.limits() != null) {
                raw.put("limits", dto.limits());
            }
            if (dto.reliability() != null) {
                raw.put("reliability", dto.reliability());
            }
            if (dto.aiRewriteEnabled() != null) {
                raw.put("ai_rewrite_enabled", dto.aiRewriteEnabled());
            }
            if (dto.rssMaxItemsPerRun() != null) {
                raw.put("rss_max_items_per_run", dto.rssMaxItemsPerRun());
            }
        }
        Map<String, Object> config = GrabberConfigSupport.withDefaults(raw);
        if (type == IngestDtos.RuleType.grabber) {
            List<String> normalized = SourceIdNormalizer.normalizeAll(GrabberConfigSupport.sourceIds(config));
            config.put("source_ids", normalized);
        }
        return config;
    }

    private void validateConfig(IngestDtos.RuleType type, Map<String, Object> config) {
        try {
            GrabberConfigSupport.scanIntervalMinutes(config);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.VALIDATION_FAILED, ex.getMessage());
        }
        if (type == IngestDtos.RuleType.grabber) {
            try {
                SourceIdNormalizer.requireValid(GrabberConfigSupport.sourceIds(config), 50);
            } catch (IllegalArgumentException ex) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.VALIDATION_FAILED, ex.getMessage());
            }
        }
        if (type == IngestDtos.RuleType.rss) {
            String url = GrabberConfigSupport.rssUrl(config);
            try {
                SsrfGuard.validateHttpUrl(url, ssrfProperties.getAllowedHosts());
            } catch (SsrfBlockedException ex) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.INGEST_SSRF_BLOCKED, ex.getMessage());
            }
        }
        if (type == IngestDtos.RuleType.website) {
            try {
                websiteFetcher.validateUrls(GrabberConfigSupport.websiteUrls(config));
            } catch (SsrfBlockedException ex) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.INGEST_SSRF_BLOCKED, ex.getMessage());
            } catch (IllegalArgumentException ex) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.VALIDATION_FAILED, ex.getMessage());
            }
        }
    }

    private void requireWebsiteEnabled(IngestDtos.RuleType type) {
        if (type == IngestDtos.RuleType.website && !grabberProperties.isWebsiteEnabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Website grabber is not enabled");
        }
    }

    private ChannelEntity requireChannel(UUID workspaceId, UUID channelId) {
        ChannelEntity channel = channelRepository
                .findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found"));
        if (!channel.getWorkspaceId().equals(workspaceId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found");
        }
        if ("reauth_required".equals(channel.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ProblemCodes.CHANNEL_REAUTH_REQUIRED, "Channel requires reauth");
        }
        return channel;
    }

    private AutomationRuleEntity requireRule(UUID ruleId) {
        return ruleRepository
                .findById(ruleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Rule not found"));
    }

    private GrabberItemEntity requireItem(UUID itemId) {
        return itemRepository
                .findById(itemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Item not found"));
    }

    private IngestDtos.GrabberRule toRuleDto(AutomationRuleEntity rule) {
        long copied = itemRepository.countByRuleIdAndStatus(rule.getId(), "scheduled")
                + itemRepository.countByRuleIdAndStatus(rule.getId(), "published");
        return new IngestDtos.GrabberRule(
                rule.getId(),
                rule.getWorkspaceId(),
                rule.getProjectId(),
                IngestDtos.RuleType.valueOf(rule.getType()),
                rule.getName(),
                rule.getTargetChannelId(),
                IngestDtos.RuleStatus.valueOf(rule.getStatus()),
                toConfigDto(rule.getConfig()),
                rule.getConfigVersion(),
                rule.getUserResponsibilityAcceptedAt(),
                rule.getUserResponsibilityAcceptedBy(),
                rule.getLastRunAt(),
                rule.getNextRunAt(),
                rule.getConsecutiveFailures(),
                rule.getCreatedBy(),
                rule.getCreatedAt(),
                rule.getUpdatedAt(),
                rule.getVersion(),
                copied);
    }

    @SuppressWarnings("unchecked")
    private IngestDtos.GrabberConfig toConfigDto(Map<String, Object> config) {
        Map<String, Object> c = GrabberConfigSupport.withDefaults(config);
        IngestDtos.PublishMode mode = IngestDtos.PublishMode.valueOf(GrabberConfigSupport.publishMode(c));
        return new IngestDtos.GrabberConfig(
                GrabberConfigSupport.sourceIds(c),
                GrabberConfigSupport.rssUrl(c),
                GrabberConfigSupport.websiteUrls(c),
                GrabberConfigSupport.websiteSelector(c),
                GrabberConfigSupport.scanIntervalMinutes(c),
                mode,
                String.valueOf(c.getOrDefault("schedule_mode", "immediate")),
                c.get("publish_delay_minutes") instanceof Number n ? n.intValue() : 0,
                (Map<String, Object>) c.get("filters"),
                (Map<String, Object>) c.get("transform"),
                (Map<String, Object>) c.get("attribution"),
                (Map<String, Object>) c.get("limits"),
                (Map<String, Object>) c.get("reliability"),
                Boolean.TRUE.equals(c.get("ai_rewrite_enabled")),
                c.get("rss_max_items_per_run") instanceof Number n ? n.intValue() : 20);
    }

    private IngestDtos.GrabberRun toRunDto(GrabberRunEntity run) {
        return new IngestDtos.GrabberRun(
                run.getId(),
                run.getRuleId(),
                run.getWorkspaceId(),
                run.getTrigger(),
                run.getStatus(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getSourcesScanned(),
                run.getItemsFound(),
                run.getItemsNew(),
                run.getItemsDeduped(),
                run.getItemsPublished(),
                run.getErrorCode(),
                run.getErrorMessage(),
                run.getTraceId());
    }

    private IngestDtos.GrabberItem toItemDto(GrabberItemEntity item) {
        return new IngestDtos.GrabberItem(
                item.getId(),
                item.getRuleId(),
                item.getRunId(),
                item.getWorkspaceId(),
                item.getSourceNetwork(),
                item.getSourceId(),
                item.getRemotePostId(),
                item.getSourcePermalink(),
                item.getSourceName(),
                item.getContentText(),
                item.getContentHash(),
                IngestDtos.ItemStatus.valueOf(item.getStatus()),
                item.getSkipReason(),
                item.getPostId(),
                item.getDetectedAt(),
                item.getModeratedAt(),
                item.getModeratedBy());
    }
}
