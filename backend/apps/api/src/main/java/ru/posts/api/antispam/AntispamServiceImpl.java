package ru.posts.api.antispam;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.antispam.engine.CompiledRuleCache;
import ru.posts.antispam.service.AntispamEventTypes;
import ru.posts.api.admin.FeatureFlagResolver;
import ru.posts.api.channel.PlanLimitService;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.antispam.AntispamDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.AntispamRuleEntity;
import ru.posts.persistence.entity.AntispamWordListEntity;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.jdbc.AntispamLogJdbc;
import ru.posts.persistence.jdbc.OutboxJdbc;
import ru.posts.persistence.repository.AntispamRuleRepository;
import ru.posts.persistence.repository.AntispamWordListRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.service.RemoteActionService;
import ru.posts.social.inbound.subscription.EventSubscriptionService;

@Service
@Profile("!openapi")
@Transactional
public class AntispamServiceImpl implements AntispamService {

    public static final String FEATURE_FLAG = "automation.antispam";
    private static final String CODE_VK_PERMISSIONS = "antispam_vk_permissions_missing";
    private static final String CODE_TG_SCAN_UNSUPPORTED = "antispam_tg_scan_unsupported";
    private static final String CODE_RESTORE_UNSUPPORTED = "antispam_restore_unsupported";

    private final WorkspaceAccessService access;
    private final PlanLimitService planLimitService;
    private final FeatureFlagResolver featureFlagResolver;
    private final AntispamRuleRepository ruleRepository;
    private final AntispamWordListRepository wordListRepository;
    private final ChannelRepository channelRepository;
    private final AntispamLogJdbc logJdbc;
    private final OutboxJdbc outboxJdbc;
    private final RemoteActionService remoteActionService;
    private final EventSubscriptionService subscriptionService;
    private final CompiledRuleCache ruleCache;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public AntispamServiceImpl(
            WorkspaceAccessService access,
            PlanLimitService planLimitService,
            FeatureFlagResolver featureFlagResolver,
            AntispamRuleRepository ruleRepository,
            AntispamWordListRepository wordListRepository,
            ChannelRepository channelRepository,
            AntispamLogJdbc logJdbc,
            OutboxJdbc outboxJdbc,
            RemoteActionService remoteActionService,
            EventSubscriptionService subscriptionService,
            CompiledRuleCache ruleCache,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate) {
        this.access = access;
        this.planLimitService = planLimitService;
        this.featureFlagResolver = featureFlagResolver;
        this.ruleRepository = ruleRepository;
        this.wordListRepository = wordListRepository;
        this.channelRepository = channelRepository;
        this.logJdbc = logJdbc;
        this.outboxJdbc = outboxJdbc;
        this.remoteActionService = remoteActionService;
        this.subscriptionService = subscriptionService;
        this.ruleCache = ruleCache;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public AntispamDtos.AntispamRulePage listRules(
            UUID userId, UUID workspaceId, UUID projectId, CursorPage.Params page) {
        access.requirePermission(workspaceId, userId, "automations.view");
        List<AntispamRuleEntity> rules =
                ruleRepository.findByWorkspaceIdAndProjectIdOrderByPriorityAscCreatedAtDesc(workspaceId, projectId);
        int limit = page == null ? 20 : page.limitOrDefault();
        List<AntispamDtos.AntispamRule> items =
                rules.stream().limit(limit + 1L).map(this::toRuleDto).toList();
        boolean hasMore = items.size() > limit;
        if (hasMore) {
            items = items.subList(0, limit);
        }
        return new AntispamDtos.AntispamRulePage(items, null, hasMore);
    }

    @Override
    public AntispamDtos.AntispamRule createRule(
            UUID userId, UUID workspaceId, UUID projectId, AntispamDtos.AntispamRuleCreate request) {
        access.requirePermission(workspaceId, userId, "automations.manage");
        ensureFeatureEnabled(workspaceId);
        planLimitService.ensureCanCreateAntispamRule(workspaceId);
        ChannelEntity channel = requireChannel(request.channelId(), workspaceId);
        AntispamRuleEntity rule = new AntispamRuleEntity();
        rule.setId(UuidV7.generate());
        rule.setWorkspaceId(workspaceId);
        rule.setProjectId(projectId);
        rule.setChannelId(channel.getId());
        rule.setName(request.name());
        rule.setStatus("draft");
        rule.setPriority(request.priority() == null ? 100 : request.priority());
        rule.setSections(request.sections() == null ? List.of() : request.sections());
        rule.setConfig(request.config() == null ? Map.of() : request.config());
        rule.setConfigVersion(1);
        rule.setDryRun(Boolean.TRUE.equals(request.dryRun()));
        rule.setCreatedBy(userId);
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        return toRuleDto(ruleRepository.save(rule));
    }

    @Override
    @Transactional(readOnly = true)
    public AntispamDtos.AntispamRule getRule(UUID userId, UUID ruleId) {
        AntispamRuleEntity rule = requireRule(ruleId);
        access.requirePermission(rule.getWorkspaceId(), userId, "automations.view");
        return toRuleDto(rule);
    }

    @Override
    public AntispamDtos.AntispamRule patchRule(UUID userId, UUID ruleId, AntispamDtos.AntispamRulePatch request) {
        AntispamRuleEntity rule = requireRule(ruleId);
        access.requirePermission(rule.getWorkspaceId(), userId, "automations.manage");
        if (request.version() != null && request.version() != rule.getVersion()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ProblemCodes.OPTIMISTIC_LOCK_CONFLICT, "Rule version mismatch");
        }
        if (request.name() != null) {
            rule.setName(request.name());
        }
        if (request.priority() != null) {
            rule.setPriority(request.priority());
        }
        if (request.sections() != null) {
            rule.setSections(request.sections());
        }
        if (request.config() != null) {
            rule.setConfig(request.config());
            rule.setConfigVersion(rule.getConfigVersion() + 1);
        }
        if (request.dryRun() != null) {
            rule.setDryRun(request.dryRun());
        }
        rule.setUpdatedAt(Instant.now());
        AntispamRuleEntity saved = ruleRepository.save(rule);
        ruleCache.invalidateChannel(saved.getChannelId());
        return toRuleDto(saved);
    }

    @Override
    public AntispamDtos.AntispamRule activate(UUID userId, UUID ruleId) {
        AntispamRuleEntity rule = requireRule(ruleId);
        access.requirePermission(rule.getWorkspaceId(), userId, "automations.manage");
        ensureFeatureEnabled(rule.getWorkspaceId());
        ChannelEntity channel = requireChannel(rule.getChannelId(), rule.getWorkspaceId());
        ensureVkCapabilities(channel, rule);
        List<String> eventTypes = AntispamEventTypes.forNetworkAndSections(channel.getNetwork(), rule.getSections());
        subscriptionService.ensure(channel.getId(), "antispam", eventTypes);
        rule.setStatus("active");
        rule.setUpdatedAt(Instant.now());
        AntispamRuleEntity saved = ruleRepository.save(rule);
        ruleCache.invalidateChannel(saved.getChannelId());
        return toRuleDto(saved);
    }

    @Override
    public AntispamDtos.AntispamRule pause(UUID userId, UUID ruleId) {
        return setStatus(userId, ruleId, "paused", "automations.manage");
    }

    @Override
    public AntispamDtos.AntispamRule resume(UUID userId, UUID ruleId) {
        AntispamRuleEntity rule = requireRule(ruleId);
        access.requirePermission(rule.getWorkspaceId(), userId, "automations.manage");
        if (!"paused".equals(rule.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Rule is not paused");
        }
        return activate(userId, ruleId);
    }

    @Override
    public AntispamDtos.AntispamRule archive(UUID userId, UUID ruleId) {
        AntispamRuleEntity rule = requireRule(ruleId);
        access.requirePermission(rule.getWorkspaceId(), userId, "automations.manage");
        rule.setStatus("archived");
        rule.setUpdatedAt(Instant.now());
        AntispamRuleEntity saved = ruleRepository.save(rule);
        ruleCache.invalidateChannel(saved.getChannelId());
        long active = ruleRepository.countByWorkspaceIdAndChannelIdAndStatusIn(
                rule.getWorkspaceId(), rule.getChannelId(), List.of("active", "paused"));
        if (active == 0) {
            try {
                subscriptionService.release(rule.getChannelId(), "antispam");
            } catch (RuntimeException ignored) {
                // subscription may already be absent
            }
        }
        return toRuleDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AntispamDtos.AntispamWordListPage listWordLists(
            UUID userId, UUID workspaceId, CursorPage.Params page) {
        access.requirePermission(workspaceId, userId, "automations.view");
        List<AntispamDtos.AntispamWordList> items = wordListRepository
                .findByWorkspaceIdOrderByNameAsc(workspaceId)
                .stream()
                .map(this::toWordListDto)
                .toList();
        return new AntispamDtos.AntispamWordListPage(items, null, false);
    }

    @Override
    public AntispamDtos.AntispamWordList createWordList(
            UUID userId, UUID workspaceId, AntispamDtos.AntispamWordListCreate request) {
        access.requirePermission(workspaceId, userId, "automations.manage");
        String mode = request.mode() == null ? "" : request.mode().toLowerCase(Locale.ROOT);
        if (!"black".equals(mode) && !"white".equals(mode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "mode must be black|white");
        }
        AntispamWordListEntity entity = new AntispamWordListEntity();
        entity.setId(UuidV7.generate());
        entity.setWorkspaceId(workspaceId);
        entity.setName(request.name());
        entity.setWords(request.words() == null ? List.of() : request.words());
        entity.setMode(mode);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return toWordListDto(wordListRepository.save(entity));
    }

    @Override
    public AntispamDtos.AntispamWordList patchWordList(
            UUID userId, UUID listId, AntispamDtos.AntispamWordListPatch request) {
        AntispamWordListEntity entity = wordListRepository
                .findById(listId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Not found"));
        access.requirePermission(entity.getWorkspaceId(), userId, "automations.manage");
        if (request.version() != null && request.version() != entity.getVersion()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ProblemCodes.OPTIMISTIC_LOCK_CONFLICT, "Word list version mismatch");
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.words() != null) {
            entity.setWords(request.words());
        }
        if (request.mode() != null) {
            String mode = request.mode().toLowerCase(Locale.ROOT);
            if (!"black".equals(mode) && !"white".equals(mode)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "mode must be black|white");
            }
            entity.setMode(mode);
        }
        entity.setUpdatedAt(Instant.now());
        AntispamWordListEntity saved = wordListRepository.save(entity);
        ruleCache.invalidateAll();
        return toWordListDto(saved);
    }

    @Override
    public void deleteWordList(UUID userId, UUID listId) {
        AntispamWordListEntity entity = wordListRepository
                .findById(listId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Not found"));
        access.requirePermission(entity.getWorkspaceId(), userId, "automations.manage");
        wordListRepository.delete(entity);
        ruleCache.invalidateAll();
    }

    @Override
    @Transactional(readOnly = true)
    public AntispamDtos.AntispamLogPage listLog(
            UUID userId,
            UUID workspaceId,
            UUID channelId,
            String section,
            UUID ruleId,
            String action,
            Instant from,
            Instant to,
            CursorPage.Params page) {
        access.requirePermission(workspaceId, userId, "automations.view");
        Instant cursorCreatedAt = null;
        UUID cursorId = null;
        if (page != null && page.cursor() != null && !page.cursor().isBlank()) {
            String[] parts = new String(Base64.getUrlDecoder().decode(page.cursor()), StandardCharsets.UTF_8)
                    .split("\\|", 2);
            if (parts.length == 2) {
                cursorCreatedAt = Instant.parse(parts[0]);
                cursorId = UUID.fromString(parts[1]);
            }
        }
        int limit = page == null ? 20 : page.limitOrDefault();
        List<AntispamLogJdbc.AntispamLogRow> rows = logJdbc.list(
                workspaceId, channelId, section, ruleId, action, from, to, cursorCreatedAt, cursorId, limit + 1);
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }
        List<AntispamDtos.AntispamLogEntry> items = rows.stream().map(this::toLogDto).toList();
        String next = null;
        if (hasMore && !rows.isEmpty()) {
            AntispamLogJdbc.AntispamLogRow last = rows.getLast();
            next = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString((last.createdAt() + "|" + last.id()).getBytes(StandardCharsets.UTF_8));
        }
        return new AntispamDtos.AntispamLogPage(items, next, hasMore);
    }

    @Override
    public AntispamDtos.AntispamLogEntry restore(
            UUID userId, UUID logId, AntispamDtos.AntispamRestoreRequest request) {
        AntispamLogJdbc.AntispamLogRow row = logJdbc
                .findById(logId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Not found"));
        access.requirePermission(row.workspaceId(), userId, "automations.moderate");
        if (!row.restorable()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    CODE_RESTORE_UNSUPPORTED,
                    "Restore is only supported for VK comments; Telegram and Max cannot restore deleted messages");
        }
        Map<String, Object> remoteRef = parseJsonMap(row.remoteRefJson());
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("commentType", remoteRef.getOrDefault("comment_type", "wall"));
        if (remoteRef.get("owner_id") != null) {
            target.put("ownerId", remoteRef.get("owner_id"));
        }
        if (remoteRef.get("post_id") != null) {
            target.put("remotePostId", remoteRef.get("post_id"));
        }
        if (remoteRef.get("comment_id") != null) {
            target.put("remoteCommentId", remoteRef.get("comment_id"));
        }
        String key = "antispam:restore:" + row.id();
        try {
            UUID actionId = remoteActionService.enqueue(
                    "restore_comment",
                    row.workspaceId(),
                    row.channelId(),
                    writeJson(target),
                    "{}",
                    key,
                    Instant.now(),
                    "antispam",
                    writeJson(Map.of("log_id", row.id().toString())),
                    userId);
            if (Boolean.TRUE.equals(request == null ? null : request.unban()) && row.authorRef() != null) {
                Map<String, Object> banTarget = new LinkedHashMap<>();
                banTarget.put("remoteUserId", row.authorRef());
                if (remoteRef.get("owner_id") != null) {
                    banTarget.put("ownerId", remoteRef.get("owner_id"));
                }
                remoteActionService.enqueue(
                        "unban_user",
                        row.workspaceId(),
                        row.channelId(),
                        writeJson(banTarget),
                        "{}",
                        key + ":unban",
                        Instant.now().plusMillis(50),
                        "antispam",
                        writeJson(Map.of("log_id", row.id().toString())),
                        userId);
            }
            logJdbc.updateState(row.id(), row.createdAt(), "restored", userId, Instant.now(), List.of(actionId));
        } catch (RuntimeException ex) {
            logJdbc.updateState(row.id(), row.createdAt(), "restore_failed", userId, Instant.now(), null);
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "antispam_restore_failed",
                    ex.getMessage() == null ? "VK restore failed" : ex.getMessage());
        }
        return toLogDto(logJdbc.findById(logId).orElse(row));
    }

    @Override
    public AntispamDtos.AntispamScanResponse scan(UUID userId, UUID ruleId) {
        AntispamRuleEntity rule = requireRule(ruleId);
        access.requirePermission(rule.getWorkspaceId(), userId, "automations.moderate");
        ChannelEntity channel = requireChannel(rule.getChannelId(), rule.getWorkspaceId());
        String network = channel.getNetwork() == null ? "" : channel.getNetwork().toLowerCase(Locale.ROOT);
        if ("tg".equals(network) || "telegram".equals(network)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    CODE_TG_SCAN_UNSUPPORTED,
                    "Telegram bots have no message history API; manual scan of last 100 is unavailable");
        }
        if (!"vk".equals(network)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.NETWORK_OPTION_UNSUPPORTED,
                    "Manual scan is currently implemented for VK only");
        }
        ensureVkCapabilities(channel, rule);
        UUID jobId = UuidV7.generate();
        outboxJdbc.append(
                "antispam_rule",
                rule.getId(),
                "antispam.scan_requested",
                writeJson(Map.of(
                        "jobId",
                        jobId.toString(),
                        "workspaceId",
                        rule.getWorkspaceId().toString(),
                        "ruleId",
                        rule.getId().toString(),
                        "channelId",
                        rule.getChannelId().toString(),
                        "dryRun",
                        rule.isDryRun(),
                        "limit",
                        100)));
        return new AntispamDtos.AntispamScanResponse(jobId, "queued", "VK wall.get + wall.getComments scan queued");
    }

    @Override
    @Transactional(readOnly = true)
    public AntispamDtos.AntispamRuleStats stats(UUID userId, UUID ruleId) {
        AntispamRuleEntity rule = requireRule(ruleId);
        access.requirePermission(rule.getWorkspaceId(), userId, "automations.view");
        Long hits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM antispam_log WHERE rule_id = ?", Long.class, ruleId);
        List<Map<String, Object>> byReasonRows = jdbcTemplate.queryForList(
                """
                SELECT unnest(reasons) AS reason, count(*) AS cnt
                FROM antispam_log WHERE rule_id = ?
                GROUP BY 1 ORDER BY cnt DESC LIMIT 50
                """,
                ruleId);
        Map<String, Long> byReason = new LinkedHashMap<>();
        for (Map<String, Object> row : byReasonRows) {
            byReason.put(String.valueOf(row.get("reason")), ((Number) row.get("cnt")).longValue());
        }
        return new AntispamDtos.AntispamRuleStats(ruleId, hits == null ? 0L : hits, byReason);
    }

    private AntispamDtos.AntispamRule setStatus(UUID userId, UUID ruleId, String status, String permission) {
        AntispamRuleEntity rule = requireRule(ruleId);
        access.requirePermission(rule.getWorkspaceId(), userId, permission);
        rule.setStatus(status);
        rule.setUpdatedAt(Instant.now());
        AntispamRuleEntity saved = ruleRepository.save(rule);
        ruleCache.invalidateChannel(saved.getChannelId());
        return toRuleDto(saved);
    }

    private void ensureFeatureEnabled(UUID workspaceId) {
        Map<String, Boolean> flags = featureFlagResolver.resolveForWorkspace(workspaceId);
        if (!Boolean.TRUE.equals(flags.get(FEATURE_FLAG))) {
            throw new ApiException(
                    HttpStatus.PAYMENT_REQUIRED,
                    ProblemCodes.PLAN_LIMIT_EXCEEDED,
                    "Antispam module is disabled for this workspace",
                    Map.of("flag", FEATURE_FLAG));
        }
    }

    private void ensureVkCapabilities(ChannelEntity channel, AntispamRuleEntity rule) {
        if (!"vk".equalsIgnoreCase(channel.getNetwork())) {
            return;
        }
        boolean needsUserToken = rule.getSections() == null
                || rule.getSections().isEmpty()
                || rule.getSections().stream()
                        .anyMatch(s -> {
                            String x = s.toLowerCase(Locale.ROOT);
                            return x.contains("wall")
                                    || x.contains("photo")
                                    || x.contains("video")
                                    || x.contains("market")
                                    || x.contains("suggest");
                        });
        if (!needsUserToken) {
            return;
        }
        Integer hasUserToken = jdbcTemplate.queryForObject(
                """
                SELECT CASE WHEN sa.access_token_enc IS NOT NULL THEN 1 ELSE 0 END
                FROM channels c
                LEFT JOIN social_accounts sa ON sa.id = c.social_account_id
                WHERE c.id = ?
                """,
                Integer.class,
                channel.getId());
        if (hasUserToken == null || hasUserToken == 0) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    CODE_VK_PERMISSIONS,
                    "VK antispam sections require a user access token with wall/groups scopes",
                    Map.of("channelId", channel.getId().toString(), "code", CODE_VK_PERMISSIONS));
        }
    }

    private AntispamRuleEntity requireRule(UUID ruleId) {
        return ruleRepository
                .findById(ruleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Not found"));
    }

    private ChannelEntity requireChannel(UUID channelId, UUID workspaceId) {
        ChannelEntity channel = channelRepository
                .findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found"));
        if (!workspaceId.equals(channel.getWorkspaceId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Channel not found");
        }
        return channel;
    }

    private AntispamDtos.AntispamRule toRuleDto(AntispamRuleEntity rule) {
        return new AntispamDtos.AntispamRule(
                rule.getId(),
                rule.getWorkspaceId(),
                rule.getProjectId(),
                rule.getChannelId(),
                rule.getName(),
                rule.getStatus(),
                rule.getPriority(),
                rule.getSections(),
                rule.getConfig(),
                rule.getConfigVersion(),
                rule.isDryRun(),
                rule.getVersion(),
                rule.getCreatedAt(),
                rule.getUpdatedAt());
    }

    private AntispamDtos.AntispamWordList toWordListDto(AntispamWordListEntity entity) {
        return new AntispamDtos.AntispamWordList(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getName(),
                entity.getWords(),
                entity.getMode(),
                entity.getVersion(),
                entity.getUpdatedAt());
    }

    private AntispamDtos.AntispamLogEntry toLogDto(AntispamLogJdbc.AntispamLogRow row) {
        return new AntispamDtos.AntispamLogEntry(
                row.id(),
                row.workspaceId(),
                row.channelId(),
                row.ruleId(),
                row.section(),
                parseJsonMap(row.remoteRefJson()),
                row.authorRef(),
                row.textSnapshot(),
                row.attachmentsSummary(),
                row.reasons(),
                row.action(),
                row.state(),
                row.restorable(),
                row.remoteActionIds(),
                row.createdAt(),
                row.restoredBy(),
                row.restoredAt());
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
