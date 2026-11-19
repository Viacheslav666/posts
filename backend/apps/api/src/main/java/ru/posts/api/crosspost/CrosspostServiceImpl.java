package ru.posts.api.crosspost;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.admin.FeatureFlagResolver;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.channel.PlanLimitService;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.crosspost.CrosspostDtos;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.api.workspace.WorkspaceMembership;
import ru.posts.common.uuid.UuidV7;
import ru.posts.crosspost.config.CrosspostConfigSupport;
import ru.posts.crosspost.filter.CrosspostFilterEngine;
import ru.posts.crosspost.loop.RuleGraphValidator;
import ru.posts.crosspost.model.CrosspostCandidate;
import ru.posts.crosspost.process.CrosspostItemProcessor;
import ru.posts.crosspost.publish.CrosspostPostFactory;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.CrosspostItemEntity;
import ru.posts.persistence.entity.CrosspostRuleEntity;
import ru.posts.persistence.entity.CrosspostRuleTargetEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.CrosspostItemRepository;
import ru.posts.persistence.repository.CrosspostRuleRepository;
import ru.posts.persistence.repository.CrosspostRuleTargetRepository;
import ru.posts.social.inbound.subscription.EventSubscriptionService;

@Service
@Profile("!openapi")
@Transactional
public class CrosspostServiceImpl implements CrosspostService {

    private static final List<String> VK_EVENTS = List.of("wall_post_new", "wall_repost");
    private static final List<String> TG_EVENTS = List.of("channel_post", "message");
    private static final List<String> MAX_EVENTS = List.of("message_created");

    private final CrosspostRuleRepository ruleRepository;
    private final CrosspostRuleTargetRepository targetRepository;
    private final CrosspostItemRepository itemRepository;
    private final ChannelRepository channelRepository;
    private final WorkspaceAccessService access;
    private final PlanLimitService planLimitService;
    private final FeatureFlagResolver featureFlagResolver;
    private final EventSubscriptionService subscriptionService;
    private final CrosspostItemProcessor itemProcessor;

    public CrosspostServiceImpl(
            CrosspostRuleRepository ruleRepository,
            CrosspostRuleTargetRepository targetRepository,
            CrosspostItemRepository itemRepository,
            ChannelRepository channelRepository,
            WorkspaceAccessService access,
            PlanLimitService planLimitService,
            FeatureFlagResolver featureFlagResolver,
            EventSubscriptionService subscriptionService,
            CrosspostItemProcessor itemProcessor) {
        this.ruleRepository = ruleRepository;
        this.targetRepository = targetRepository;
        this.itemRepository = itemRepository;
        this.channelRepository = channelRepository;
        this.access = access;
        this.planLimitService = planLimitService;
        this.featureFlagResolver = featureFlagResolver;
        this.subscriptionService = subscriptionService;
        this.itemProcessor = itemProcessor;
    }

    @Override
    @Transactional(readOnly = true)
    public CrosspostDtos.RuleList listRules(UUID userId, UUID workspaceId, UUID projectId) {
        WorkspaceMembership membership = access.requirePermission(workspaceId, userId, "automations.view");
        access.requireVisibleProject(membership, projectId);
        List<CrosspostRuleEntity> rules =
                ruleRepository.findByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(workspaceId, projectId);
        List<CrosspostDtos.Rule> items = new ArrayList<>();
        for (CrosspostRuleEntity rule : rules) {
            items.add(toDto(rule, false));
        }
        return new CrosspostDtos.RuleList(items);
    }

    @Override
    public CrosspostDtos.Rule createRule(
            UUID userId,
            UUID workspaceId,
            UUID projectId,
            CrosspostDtos.RuleCreate request,
            String idempotencyKey,
            ClientRequestContext ctx) {
        WorkspaceMembership membership = access.requirePermission(workspaceId, userId, "automations.manage");
        access.requireVisibleProject(membership, projectId);
        requireFeature(workspaceId);
        planLimitService.ensureCanCreateCrosspostRule(workspaceId);

        ChannelEntity source = requireChannel(workspaceId, request.sourceChannelId());
        requireSourceNetwork(source.getNetwork());
        Map<String, Object> config = CrosspostConfigSupport.withDefaults(request.config());
        Instant now = Instant.now();
        CrosspostRuleEntity rule = new CrosspostRuleEntity();
        rule.setId(UuidV7.generate());
        rule.setWorkspaceId(workspaceId);
        rule.setProjectId(projectId);
        rule.setSourceChannelId(source.getId());
        rule.setName(request.name().trim());
        rule.setStatus("draft");
        rule.setConfig(config);
        rule.setCreatedBy(userId);
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        ruleRepository.save(rule);
        replaceTargets(rule, request.targets());
        if (Boolean.TRUE.equals(request.activate())) {
            activateInternal(rule);
        }
        return toDto(rule, true);
    }

    @Override
    @Transactional(readOnly = true)
    public CrosspostDtos.Rule getRule(UUID userId, UUID ruleId) {
        CrosspostRuleEntity rule = requireRule(ruleId);
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.view");
        access.requireVisibleProject(membership, rule.getProjectId());
        return toDto(rule, true);
    }

    @Override
    public CrosspostDtos.Rule patchRule(
            UUID userId, UUID ruleId, CrosspostDtos.RulePatch request, ClientRequestContext ctx) {
        CrosspostRuleEntity rule = requireRule(ruleId);
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.manage");
        access.requireVisibleProject(membership, rule.getProjectId());
        if (request.version() == null || request.version() != rule.getVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.OPTIMISTIC_LOCK_CONFLICT, "Rule version mismatch");
        }
        if (request.name() != null) {
            rule.setName(request.name().trim());
        }
        if (request.config() != null) {
            rule.setConfig(CrosspostConfigSupport.withDefaults(request.config()));
            rule.setConfigVersion(rule.getConfigVersion() + 1);
        }
        if (request.targets() != null) {
            replaceTargets(rule, request.targets());
        }
        rule.setUpdatedAt(Instant.now());
        ruleRepository.save(rule);
        return toDto(rule, true);
    }

    @Override
    public CrosspostDtos.Rule activate(UUID userId, UUID ruleId, ClientRequestContext ctx) {
        CrosspostRuleEntity rule = requireRule(ruleId);
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.manage");
        access.requireVisibleProject(membership, rule.getProjectId());
        requireFeature(rule.getWorkspaceId());
        activateInternal(rule);
        return toDto(rule, true);
    }

    @Override
    public CrosspostDtos.Rule pause(UUID userId, UUID ruleId, ClientRequestContext ctx) {
        CrosspostRuleEntity rule = requireRule(ruleId);
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.manage");
        access.requireVisibleProject(membership, rule.getProjectId());
        rule.setStatus("paused");
        rule.setUpdatedAt(Instant.now());
        ruleRepository.save(rule);
        return toDto(rule, true);
    }

    @Override
    @Transactional(readOnly = true)
    public CrosspostDtos.PreviewResponse preview(UUID userId, UUID ruleId, CrosspostDtos.PreviewRequest request) {
        CrosspostRuleEntity rule = requireRule(ruleId);
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.view");
        access.requireVisibleProject(membership, rule.getProjectId());
        CrosspostCandidate candidate = previewCandidate(rule, request);
        List<String> filterReasons = CrosspostFilterEngine.evaluate(candidate, rule.getConfig());
        List<CrosspostRuleTargetEntity> targets = targetRepository.findByRuleIdOrderByCreatedAtAsc(rule.getId());
        Map<UUID, ChannelEntity> channels = new HashMap<>();
        channelRepository
                .findByIdAndDeletedAtIsNull(rule.getSourceChannelId())
                .ifPresent(c -> channels.put(c.getId(), c));
        for (CrosspostRuleTargetEntity t : targets) {
            channelRepository
                    .findByIdAndDeletedAtIsNull(t.getTargetChannelId())
                    .ifPresent(c -> channels.put(c.getId(), c));
        }
        CrosspostPostFactory.PublishResult result = itemProcessor.preview(rule, candidate, targets, channels);
        List<CrosspostDtos.PreviewTarget> out = new ArrayList<>();
        for (CrosspostPostFactory.TargetPublish tp : result.targets()) {
            ChannelEntity ch = channels.get(tp.targetChannelId());
            out.add(new CrosspostDtos.PreviewTarget(
                    tp.targetChannelId(), ch == null ? null : ch.getNetwork(), tp.status(), tp.reason(), tp.preview()));
        }
        return new CrosspostDtos.PreviewResponse(filterReasons, out);
    }

    @Override
    @Transactional(readOnly = true)
    public CrosspostDtos.ItemPage listItems(UUID userId, UUID ruleId, String status, String cursor, int limit) {
        CrosspostRuleEntity rule = requireRule(ruleId);
        WorkspaceMembership membership = access.requirePermission(rule.getWorkspaceId(), userId, "automations.view");
        access.requireVisibleProject(membership, rule.getProjectId());
        int pageSize = Math.min(Math.max(limit, 1), 100);
        int offset = decodeCursor(cursor);
        String statusFilter = status == null || status.isBlank() ? null : status;
        List<CrosspostItemEntity> rows = itemRepository.pageByRule(ruleId, statusFilter, pageSize + 1, offset);
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = rows.subList(0, pageSize);
        }
        List<CrosspostDtos.Item> items = rows.stream().map(this::toItemDto).toList();
        String next = hasMore ? encodeCursor(offset + pageSize) : null;
        return new CrosspostDtos.ItemPage(items, next);
    }

    @Override
    public CrosspostDtos.Item retry(UUID userId, UUID itemId, ClientRequestContext ctx) {
        CrosspostItemEntity item = itemRepository
                .findById(itemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Not found"));
        CrosspostRuleEntity rule = requireRule(item.getRuleId());
        if (!rule.getWorkspaceId().equals(item.getWorkspaceId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Not found");
        }
        WorkspaceMembership membership =
                access.requirePermission(rule.getWorkspaceId(), userId, "automations.moderate");
        access.requireVisibleProject(membership, rule.getProjectId());
        if (!"failed".equals(item.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Only failed items can be retried");
        }
        item.setStatus("received");
        item.setProcessAfter(Instant.now());
        item.setReasons(new String[0]);
        item.setPostId(null);
        item.setPublicationIds(new UUID[0]);
        item.setUpdatedAt(Instant.now());
        itemRepository.save(item);
        return toItemDto(item);
    }

    private void activateInternal(CrosspostRuleEntity rule) {
        List<CrosspostRuleTargetEntity> targets = targetRepository.findByRuleIdOrderByCreatedAtAsc(rule.getId());
        if (targets.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "At least one target required");
        }
        ChannelEntity source = requireChannel(rule.getWorkspaceId(), rule.getSourceChannelId());
        subscriptionService.ensure(source.getId(), "crosspost", eventTypesFor(source.getNetwork()));
        rule.setStatus("active");
        rule.setUpdatedAt(Instant.now());
        ruleRepository.save(rule);
    }

    private void replaceTargets(CrosspostRuleEntity rule, List<CrosspostDtos.TargetRequest> targets) {
        targetRepository.deleteByRuleId(rule.getId());
        if (targets == null) {
            return;
        }
        Instant now = Instant.now();
        for (CrosspostDtos.TargetRequest t : targets) {
            if (t.targetChannelId().equals(rule.getSourceChannelId())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Source and target must differ");
            }
            ChannelEntity channel = requireChannel(rule.getWorkspaceId(), t.targetChannelId());
            requireTargetNetwork(channel.getNetwork());
            CrosspostRuleTargetEntity row = new CrosspostRuleTargetEntity();
            row.setId(UuidV7.generate());
            row.setRuleId(rule.getId());
            row.setTargetChannelId(channel.getId());
            row.setOptions(t.options() == null ? Map.of() : t.options());
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            targetRepository.save(row);
        }
    }

    private List<CrosspostDtos.Warning> graphWarnings(CrosspostRuleEntity rule) {
        List<RuleGraphValidator.Edge> edges = new ArrayList<>();
        // include all active/paused rules in workspace for cycle detection
        List<CrosspostRuleEntity> all = ruleRepository.findByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(
                rule.getWorkspaceId(), rule.getProjectId());
        for (CrosspostRuleEntity r : all) {
            if ("draft".equals(r.getStatus())) {
                continue;
            }
            for (CrosspostRuleTargetEntity t : targetRepository.findByRuleIdOrderByCreatedAtAsc(r.getId())) {
                edges.add(new RuleGraphValidator.Edge(r.getId(), r.getSourceChannelId(), t.getTargetChannelId()));
            }
        }
        // also include current draft targets
        for (CrosspostRuleTargetEntity t : targetRepository.findByRuleIdOrderByCreatedAtAsc(rule.getId())) {
            edges.add(new RuleGraphValidator.Edge(rule.getId(), rule.getSourceChannelId(), t.getTargetChannelId()));
        }
        return RuleGraphValidator.validate(edges).stream()
                .map(w -> new CrosspostDtos.Warning(w.code(), w.message(), w.channelPath()))
                .toList();
    }

    private CrosspostDtos.Rule toDto(CrosspostRuleEntity rule, boolean withWarnings) {
        List<CrosspostRuleTargetEntity> targets = targetRepository.findByRuleIdOrderByCreatedAtAsc(rule.getId());
        List<CrosspostDtos.Target> targetDtos = new ArrayList<>();
        for (CrosspostRuleTargetEntity t : targets) {
            ChannelEntity ch = channelRepository
                    .findByIdAndDeletedAtIsNull(t.getTargetChannelId())
                    .orElse(null);
            targetDtos.add(new CrosspostDtos.Target(
                    t.getId(), t.getTargetChannelId(), ch == null ? null : ch.getNetwork(), t.getOptions()));
        }
        ChannelEntity source = channelRepository
                .findByIdAndDeletedAtIsNull(rule.getSourceChannelId())
                .orElse(null);
        List<CrosspostDtos.Warning> warnings = withWarnings ? graphWarnings(rule) : List.of();
        return new CrosspostDtos.Rule(
                rule.getId(),
                rule.getWorkspaceId(),
                rule.getProjectId(),
                rule.getSourceChannelId(),
                source == null ? null : source.getNetwork(),
                rule.getName(),
                CrosspostDtos.RuleStatus.valueOf(rule.getStatus()),
                rule.getConfig(),
                rule.getConfigVersion(),
                rule.getVersion(),
                targetDtos,
                warnings,
                rule.getCreatedAt(),
                rule.getUpdatedAt());
    }

    private CrosspostDtos.Item toItemDto(CrosspostItemEntity item) {
        return new CrosspostDtos.Item(
                item.getId(),
                item.getRuleId(),
                item.getSourceChannelId(),
                item.getSourceRemoteId(),
                item.getSourceNetwork(),
                CrosspostDtos.ItemStatus.valueOf(item.getStatus()),
                item.getReasons() == null ? List.of() : List.of(item.getReasons()),
                item.getPostId(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    private CrosspostCandidate previewCandidate(CrosspostRuleEntity rule, CrosspostDtos.PreviewRequest request) {
        String remoteId = request.sourceRemoteId();
        if ((remoteId == null || remoteId.isBlank()) && request.sourcePostUrl() != null) {
            remoteId = extractRemoteId(request.sourcePostUrl());
        }
        if (remoteId == null || remoteId.isBlank()) {
            remoteId = "preview";
        }
        String text = "";
        if (request.candidate() != null && request.candidate().get("text") != null) {
            text = request.candidate().get("text").toString();
        }
        ChannelEntity source = channelRepository
                .findByIdAndDeletedAtIsNull(rule.getSourceChannelId())
                .orElse(null);
        return CrosspostCandidate.builder()
                .network(source == null ? "vk" : source.getNetwork())
                .remoteId(remoteId)
                .text(text)
                .attachments(List.of())
                .markedAsAds(request.candidate() != null
                        && Boolean.TRUE.equals(request.candidate().get("markedAsAds")))
                .hasErid(request.candidate() != null
                        && Boolean.TRUE.equals(request.candidate().get("hasErid")))
                .isRepost(request.candidate() != null
                        && Boolean.TRUE.equals(request.candidate().get("isRepost")))
                .hasImage(request.candidate() != null
                        && Boolean.TRUE.equals(request.candidate().get("hasImage")))
                .hasVideo(request.candidate() != null
                        && Boolean.TRUE.equals(request.candidate().get("hasVideo")))
                .hasPoll(request.candidate() != null
                        && Boolean.TRUE.equals(request.candidate().get("hasPoll")))
                .hasGeo(request.candidate() != null
                        && Boolean.TRUE.equals(request.candidate().get("hasGeo")))
                .build();
    }

    private static String extractRemoteId(String url) {
        if (url == null) {
            return null;
        }
        int idx = url.lastIndexOf('_');
        if (idx > 0 && idx < url.length() - 1) {
            return url.substring(idx + 1).replaceAll("[^0-9]", "");
        }
        int slash = url.lastIndexOf('/');
        if (slash >= 0 && slash < url.length() - 1) {
            return url.substring(slash + 1).replaceAll("[^0-9]", "");
        }
        return url;
    }

    private void requireFeature(UUID workspaceId) {
        Map<String, Boolean> features = featureFlagResolver.resolveForWorkspace(workspaceId);
        if (Boolean.FALSE.equals(features.get("automation.crosspost"))) {
            throw new ApiException(
                    HttpStatus.PAYMENT_REQUIRED,
                    ProblemCodes.PLAN_LIMIT_EXCEEDED,
                    "Crosspost feature disabled",
                    Map.of("limit", "automation.crosspost"));
        }
    }

    private CrosspostRuleEntity requireRule(UUID ruleId) {
        return ruleRepository
                .findById(ruleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Not found"));
    }

    private ChannelEntity requireChannel(UUID workspaceId, UUID channelId) {
        ChannelEntity channel = channelRepository
                .findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found"));
        if (!workspaceId.equals(channel.getWorkspaceId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found");
        }
        return channel;
    }

    private static void requireSourceNetwork(String network) {
        if (!"vk".equals(network) && !"tg".equals(network) && !"max".equals(network)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Unsupported source network");
        }
    }

    private static void requireTargetNetwork(String network) {
        if (!"vk".equals(network) && !"tg".equals(network) && !"ok".equals(network) && !"max".equals(network)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Unsupported target network");
        }
    }

    private static List<String> eventTypesFor(String network) {
        return switch (network) {
            case "vk" -> VK_EVENTS;
            case "tg" -> TG_EVENTS;
            case "max" -> MAX_EVENTS;
            default -> List.of();
        };
    }

    private static int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return 0;
        }
    }

    private static String encodeCursor(int offset) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(Integer.toString(offset).getBytes(StandardCharsets.UTF_8));
    }
}
