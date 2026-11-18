package ru.posts.api.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.admin.AdminDtos;
import ru.posts.api.web.dto.billing.BillingDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.support.CursorPages;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.AnnouncementEntity;
import ru.posts.persistence.entity.FeatureFlagEntity;
import ru.posts.persistence.entity.LedgerEntryEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.PromoCodeEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.entity.SubscriptionEntity;
import ru.posts.persistence.entity.UserEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.entity.WorkspaceFeatureFlagEntity;
import ru.posts.persistence.entity.WorkspaceMemberEntity;
import ru.posts.persistence.repository.AnnouncementRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.FeatureFlagRepository;
import ru.posts.persistence.repository.LedgerEntryRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.PromoCodeRepository;
import ru.posts.persistence.repository.PublicationRepository;
import ru.posts.persistence.repository.SubscriptionRepository;
import ru.posts.persistence.repository.UserRepository;
import ru.posts.persistence.repository.WorkspaceFeatureFlagRepository;
import ru.posts.persistence.repository.WorkspaceMemberRepository;
import ru.posts.persistence.repository.WorkspaceRepository;
import ru.posts.security.jwt.JwtService;

@Service
@Profile("!openapi")
public class AdminServiceImpl implements AdminService {

    private final PlatformAdminGuard platformAdminGuard;
    private final AdminAuditRecorder adminAuditRecorder;
    private final AdminQueueService adminQueueService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final PlanRepository planRepository;
    private final ChannelRepository channelRepository;
    private final PublicationRepository publicationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final WorkspaceFeatureFlagRepository workspaceFeatureFlagRepository;
    private final AnnouncementRepository announcementRepository;
    private final ru.posts.persistence.repository.AdminAuditLogRepository adminAuditLogRepository;

    public AdminServiceImpl(
            PlatformAdminGuard platformAdminGuard,
            AdminAuditRecorder adminAuditRecorder,
            AdminQueueService adminQueueService,
            JwtService jwtService,
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            PlanRepository planRepository,
            ChannelRepository channelRepository,
            PublicationRepository publicationRepository,
            SubscriptionRepository subscriptionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            PromoCodeRepository promoCodeRepository,
            FeatureFlagRepository featureFlagRepository,
            WorkspaceFeatureFlagRepository workspaceFeatureFlagRepository,
            AnnouncementRepository announcementRepository,
            ru.posts.persistence.repository.AdminAuditLogRepository adminAuditLogRepository) {
        this.platformAdminGuard = platformAdminGuard;
        this.adminAuditRecorder = adminAuditRecorder;
        this.adminQueueService = adminQueueService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.planRepository = planRepository;
        this.channelRepository = channelRepository;
        this.publicationRepository = publicationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.featureFlagRepository = featureFlagRepository;
        this.workspaceFeatureFlagRepository = workspaceFeatureFlagRepository;
        this.announcementRepository = announcementRepository;
        this.adminAuditLogRepository = adminAuditLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.AdminUserPage searchUsers(UUID actorId, String q, Boolean platformAdmin, CursorPage.Params page) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        List<UserEntity> all = userRepository.search(blankToNull(q), platformAdmin);
        CursorPages.Slice<UserEntity> slice =
                CursorPages.slice(all, page.cursor(), page.limitOrDefault(), UserEntity::getId);
        List<AdminDtos.UserSummary> items = slice.items().stream()
                .map(u -> new AdminDtos.UserSummary(
                        u.getId(),
                        u.getEmail(),
                        u.getName(),
                        u.getEmailVerifiedAt() != null,
                        u.isPlatformAdmin(),
                        u.isTotpEnabled(),
                        u.getCreatedAt(),
                        workspaceMemberRepository.findByUserId(u.getId()).size()))
                .toList();
        return new CursorPage.AdminUserPage(items, slice.nextCursor(), slice.hasMore());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDtos.UserCard getUser(UUID actorId, UUID userId) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        UserEntity user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "User not found"));
        List<AdminDtos.UserWorkspaceLink> links = new ArrayList<>();
        for (WorkspaceMemberEntity member : workspaceMemberRepository.findByUserId(userId)) {
            WorkspaceEntity ws =
                    workspaceRepository.findById(member.getWorkspaceId()).orElse(null);
            if (ws == null || ws.getDeletedAt() != null) {
                continue;
            }
            links.add(new AdminDtos.UserWorkspaceLink(
                    ws.getId(),
                    ws.getName(),
                    ApiEnums.MemberRole.valueOf(member.getRole()),
                    ApiEnums.BillingStatus.valueOf(ws.getBillingStatus())));
        }
        return new AdminDtos.UserCard(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getEmailVerifiedAt() != null,
                user.isPlatformAdmin(),
                user.isTotpEnabled(),
                user.getCreatedAt(),
                links);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.AdminWorkspacePage searchWorkspaces(
            UUID actorId, String q, String billingStatus, String planCode, CursorPage.Params page) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        UUID planId = null;
        if (planCode != null && !planCode.isBlank()) {
            planId = planRepository
                    .findByCode(planCode)
                    .map(PlanEntity::getId)
                    .orElseThrow(
                            () -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Plan not found"));
        }
        List<WorkspaceEntity> all =
                workspaceRepository.search(blankToNull(q), blankToNull(billingStatus), planId, null);
        CursorPages.Slice<WorkspaceEntity> slice =
                CursorPages.slice(all, page.cursor(), page.limitOrDefault(), WorkspaceEntity::getId);
        List<AdminDtos.WorkspaceSummary> items = new ArrayList<>();
        for (WorkspaceEntity ws : slice.items()) {
            UserEntity owner = userRepository.findById(ws.getOwnerUserId()).orElse(null);
            String plan = ws.getPlanId() == null
                    ? null
                    : planRepository
                            .findById(ws.getPlanId())
                            .map(PlanEntity::getCode)
                            .orElse(null);
            items.add(new AdminDtos.WorkspaceSummary(
                    ws.getId(),
                    ws.getName(),
                    ws.getOwnerUserId(),
                    owner == null ? null : owner.getEmail(),
                    plan,
                    ApiEnums.BillingStatus.valueOf(ws.getBillingStatus()),
                    ws.getCreatedAt(),
                    channelRepository.countActiveByWorkspaceId(ws.getId())));
        }
        return new CursorPage.AdminWorkspacePage(items, slice.nextCursor(), slice.hasMore());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDtos.WorkspaceCard getWorkspace(UUID actorId, UUID workspaceId) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        return toWorkspaceCard(requireWorkspace(workspaceId));
    }

    @Override
    @Transactional
    public AdminDtos.ImpersonateResponse impersonate(
            UUID actorId, UUID targetUserId, AdminDtos.ImpersonateRequest request, UUID actorSessionId, String ip) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        if (actorId.equals(targetUserId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Cannot impersonate yourself");
        }
        UserEntity target = userRepository
                .findById(targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "User not found"));
        if (target.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "User not found");
        }
        if (actorSessionId == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Session required for impersonation");
        }
        JwtService.IssuedAccessToken token =
                jwtService.issueImpersonationAccessToken(actorId, targetUserId, actorSessionId, request.reason());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("targetEmail", target.getEmail());
        details.put("expiresAt", token.expiresAt().toString());
        if (request.workspaceId() != null) {
            details.put("workspaceId", request.workspaceId().toString());
        }
        adminAuditRecorder.record(actorId, "admin.impersonate", "user", targetUserId, request.reason(), details, ip);
        return new AdminDtos.ImpersonateResponse(
                token.token(),
                token.expiresInSeconds(),
                token.expiresAt(),
                target.getId(),
                target.getEmail(),
                request.reason(),
                true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BillingDtos.Plan> listPlans(UUID actorId) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        return planRepository.findAllByOrderByPriceMonthAsc().stream()
                .map(this::toPlan)
                .toList();
    }

    @Override
    @Transactional
    public BillingDtos.Plan upsertPlan(UUID actorId, AdminDtos.PlanUpsertRequest request, String ip) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        PlanEntity plan = planRepository.findByCode(request.code()).orElseGet(PlanEntity::new);
        boolean created = plan.getId() == null;
        if (created) {
            plan.setId(UuidV7.generate());
            plan.setCreatedAt(Instant.now());
        }
        plan.setCode(request.code());
        plan.setName(request.name());
        plan.setPriceMonth(request.priceMonth());
        plan.setPriceYear(request.priceYear());
        plan.setLimits(new HashMap<>(request.limits()));
        plan = planRepository.save(plan);
        adminAuditRecorder.record(
                actorId,
                created ? "admin.plan.create" : "admin.plan.update",
                "plan",
                plan.getId(),
                null,
                Map.of("code", plan.getCode()),
                ip);
        return toPlan(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.AdminPromoPage listPromos(UUID actorId, CursorPage.Params page) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        List<PromoCodeEntity> all = promoCodeRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
        CursorPages.Slice<PromoCodeEntity> slice =
                CursorPages.slice(all, page.cursor(), page.limitOrDefault(), PromoCodeEntity::getId);
        return new CursorPage.AdminPromoPage(
                slice.items().stream().map(this::toPromo).toList(), slice.nextCursor(), slice.hasMore());
    }

    @Override
    @Transactional
    public AdminDtos.PromoCode createPromo(UUID actorId, AdminDtos.PromoCreateRequest request, String ip) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        if (promoCodeRepository.findByCodeIgnoreCase(request.code()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Promo code already exists");
        }
        PromoCodeEntity promo = new PromoCodeEntity();
        promo.setId(UuidV7.generate());
        promo.setCode(request.code().trim());
        promo.setKind(request.kind());
        promo.setValue(request.value());
        promo.setValidFrom(request.validFrom());
        promo.setValidUntil(request.validUntil());
        promo.setMaxRedemptions(request.maxRedemptions());
        promo.setStackable(request.stackable());
        promo.setOncePerWorkspace(request.oncePerWorkspace());
        promo.setActive(request.active());
        promo.setCreatedAt(Instant.now());
        promo = promoCodeRepository.save(promo);
        adminAuditRecorder.record(
                actorId, "admin.promo.create", "promo_code", promo.getId(), null, Map.of("code", promo.getCode()), ip);
        return toPromo(promo);
    }

    @Override
    @Transactional
    public AdminDtos.PromoCode updatePromo(
            UUID actorId, UUID promoId, AdminDtos.PromoUpdateRequest request, String ip) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        PromoCodeEntity promo = promoCodeRepository
                .findById(promoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Promo not found"));
        if (request.validFrom() != null) {
            promo.setValidFrom(request.validFrom());
        }
        if (request.validUntil() != null) {
            promo.setValidUntil(request.validUntil());
        }
        if (request.maxRedemptions() != null) {
            promo.setMaxRedemptions(request.maxRedemptions());
        }
        if (request.stackable() != null) {
            promo.setStackable(request.stackable());
        }
        if (request.oncePerWorkspace() != null) {
            promo.setOncePerWorkspace(request.oncePerWorkspace());
        }
        if (request.active() != null) {
            promo.setActive(request.active());
        }
        promo = promoCodeRepository.save(promo);
        adminAuditRecorder.record(
                actorId, "admin.promo.update", "promo_code", promo.getId(), null, Map.of("code", promo.getCode()), ip);
        return toPromo(promo);
    }

    @Override
    @Transactional
    public AdminDtos.WorkspaceCard adjustSubscription(
            UUID actorId, UUID workspaceId, AdminDtos.AdjustSubscriptionRequest request, String ip) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        PlanEntity plan = planRepository
                .findByCode(request.planCode())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Plan not found"));
        workspace.setPlanId(plan.getId());
        if (request.billingStatus() != null) {
            workspace.setBillingStatus(request.billingStatus().name());
        }
        if (request.periodEnd() != null) {
            workspace.setCurrentPeriodEnd(request.periodEnd());
        }
        if (request.trialEndsAt() != null) {
            workspace.setTrialEndsAt(request.trialEndsAt());
        }
        workspace.setUpdatedAt(Instant.now());
        workspaceRepository.save(workspace);

        SubscriptionEntity sub = subscriptionRepository
                .findFirstByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .orElseGet(SubscriptionEntity::new);
        if (sub.getId() == null) {
            sub.setId(UuidV7.generate());
            sub.setWorkspaceId(workspaceId);
            sub.setPeriodStart(Instant.now());
            sub.setCreatedAt(Instant.now());
        }
        sub.setPlanId(plan.getId());
        sub.setStatus(
                request.billingStatus() == null
                        ? workspace.getBillingStatus()
                        : request.billingStatus().name());
        if (request.periodEnd() != null) {
            sub.setPeriodEnd(request.periodEnd());
        } else if (sub.getPeriodEnd() == null) {
            sub.setPeriodEnd(Instant.now().plusSeconds(30L * 24 * 3600));
        }
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);

        adminAuditRecorder.record(
                actorId,
                "admin.subscription.adjust",
                "workspace",
                workspaceId,
                request.reason(),
                Map.of("planCode", plan.getCode(), "billingStatus", workspace.getBillingStatus()),
                ip);
        return toWorkspaceCard(workspace);
    }

    @Override
    @Transactional
    public AdminDtos.LedgerEntry adjustBalance(
            UUID actorId, UUID workspaceId, AdminDtos.AdjustBalanceRequest request, String ip) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        requireWorkspace(workspaceId);
        LedgerEntryEntity entry = new LedgerEntryEntity();
        entry.setId(UuidV7.generate());
        entry.setWorkspaceId(workspaceId);
        entry.setAmount(request.amount());
        entry.setKind(request.kind() == null || request.kind().isBlank() ? "admin_adjustment" : request.kind());
        entry.setDescription(request.reason());
        entry.setCreatedAt(Instant.now());
        entry = ledgerEntryRepository.save(entry);
        adminAuditRecorder.record(
                actorId,
                "admin.balance.adjust",
                "workspace",
                workspaceId,
                request.reason(),
                Map.of(
                        "amount",
                        request.amount().toPlainString(),
                        "ledgerId",
                        entry.getId().toString()),
                ip);
        return new AdminDtos.LedgerEntry(
                entry.getId(),
                entry.getWorkspaceId(),
                entry.getAmount(),
                entry.getKind(),
                entry.getDescription(),
                entry.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminDtos.QueueInfo> listQueues(UUID actorId) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        return adminQueueService.listQueues();
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.AdminQueueMessagePage listQueueMessages(UUID actorId, String queue, CursorPage.Params page) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        return adminQueueService.listMessages(queue, page);
    }

    @Override
    @Transactional
    public AdminDtos.QueueMessageActionResponse retryQueueMessage(
            UUID actorId, String queue, String messageId, String idempotencyKey, String ip) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        AdminDtos.QueueMessageActionResponse response =
                adminQueueService.retry(queue, messageId, idempotencyKey, actorId);
        adminAuditRecorder.record(
                actorId,
                "admin.queue.retry",
                "queue_message",
                null,
                null,
                Map.of("queue", queue, "messageId", messageId, "applied", response.applied()),
                ip);
        return response;
    }

    @Override
    @Transactional
    public AdminDtos.QueueMessageActionResponse deleteQueueMessage(
            UUID actorId, String queue, String messageId, String idempotencyKey, String ip) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        AdminDtos.QueueMessageActionResponse response =
                adminQueueService.delete(queue, messageId, idempotencyKey, actorId);
        adminAuditRecorder.record(
                actorId,
                "admin.queue.delete",
                "queue_message",
                null,
                null,
                Map.of("queue", queue, "messageId", messageId, "applied", response.applied()),
                ip);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminDtos.FeatureFlag> listFeatureFlags(UUID actorId) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        return featureFlagRepository.findAllByOrderByKeyAsc().stream()
                .map(this::toFlag)
                .toList();
    }

    @Override
    @Transactional
    public AdminDtos.FeatureFlag upsertFeatureFlag(
            UUID actorId, AdminDtos.FeatureFlagUpsertRequest request, String ip) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        FeatureFlagEntity flag = featureFlagRepository.findByKey(request.key()).orElseGet(FeatureFlagEntity::new);
        boolean created = flag.getId() == null;
        if (created) {
            flag.setId(UuidV7.generate());
            flag.setKey(request.key());
            flag.setCreatedAt(Instant.now());
        }
        flag.setDescription(request.description());
        flag.setEnabledGlobal(request.enabledGlobal());
        flag.setUpdatedAt(Instant.now());
        flag = featureFlagRepository.save(flag);
        adminAuditRecorder.record(
                actorId,
                created ? "admin.feature_flag.create" : "admin.feature_flag.update",
                "feature_flag",
                flag.getId(),
                null,
                Map.of("key", flag.getKey(), "enabledGlobal", flag.isEnabledGlobal()),
                ip);
        return toFlag(flag);
    }

    @Override
    @Transactional
    public AdminDtos.WorkspaceFeatureFlag setWorkspaceFeatureFlag(
            UUID actorId, UUID workspaceId, String flagKey, AdminDtos.WorkspaceFeatureFlagRequest request, String ip) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        requireWorkspace(workspaceId);
        if (!featureFlagRepository.existsByKey(flagKey)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Feature flag not found");
        }
        WorkspaceFeatureFlagEntity override = workspaceFeatureFlagRepository
                .findByWorkspaceIdAndFlagKey(workspaceId, flagKey)
                .orElseGet(WorkspaceFeatureFlagEntity::new);
        if (override.getId() == null) {
            override.setId(UuidV7.generate());
            override.setWorkspaceId(workspaceId);
            override.setFlagKey(flagKey);
            override.setCreatedAt(Instant.now());
        }
        override.setEnabled(Boolean.TRUE.equals(request.enabled()));
        override.setUpdatedAt(Instant.now());
        override = workspaceFeatureFlagRepository.save(override);
        adminAuditRecorder.record(
                actorId,
                "admin.feature_flag.workspace",
                "workspace",
                workspaceId,
                null,
                Map.of("flagKey", flagKey, "enabled", override.isEnabled()),
                ip);
        return new AdminDtos.WorkspaceFeatureFlag(
                override.getWorkspaceId(), override.getFlagKey(), override.isEnabled(), override.getUpdatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.AdminAnnouncementPage listAnnouncements(UUID actorId, CursorPage.Params page) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        List<AnnouncementEntity> all = announcementRepository.findAllByOrderByCreatedAtDesc();
        CursorPages.Slice<AnnouncementEntity> slice =
                CursorPages.slice(all, page.cursor(), page.limitOrDefault(), AnnouncementEntity::getId);
        return new CursorPage.AdminAnnouncementPage(
                slice.items().stream().map(this::toAnnouncement).toList(), slice.nextCursor(), slice.hasMore());
    }

    @Override
    @Transactional
    public AdminDtos.Announcement createAnnouncement(
            UUID actorId, AdminDtos.AnnouncementCreateRequest request, String ip) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        AnnouncementEntity entity = new AnnouncementEntity();
        entity.setId(UuidV7.generate());
        entity.setTitle(request.title());
        entity.setBody(request.body());
        entity.setSeverity(request.severity());
        entity.setActive(request.active());
        entity.setStartsAt(request.startsAt() == null ? Instant.now() : request.startsAt());
        entity.setEndsAt(request.endsAt());
        entity.setCreatedBy(actorId);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity = announcementRepository.save(entity);
        adminAuditRecorder.record(
                actorId, "admin.announcement.create", "announcement", entity.getId(), null, Map.of(), ip);
        return toAnnouncement(entity);
    }

    @Override
    @Transactional
    public AdminDtos.Announcement updateAnnouncement(
            UUID actorId, UUID announcementId, AdminDtos.AnnouncementUpdateRequest request, String ip) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        AnnouncementEntity entity = announcementRepository
                .findById(announcementId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Announcement not found"));
        if (request.title() != null) {
            entity.setTitle(request.title());
        }
        if (request.body() != null) {
            entity.setBody(request.body());
        }
        if (request.severity() != null) {
            entity.setSeverity(request.severity());
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        if (request.startsAt() != null) {
            entity.setStartsAt(request.startsAt());
        }
        if (request.endsAt() != null) {
            entity.setEndsAt(request.endsAt());
        }
        entity.setUpdatedAt(Instant.now());
        entity = announcementRepository.save(entity);
        adminAuditRecorder.record(
                actorId, "admin.announcement.update", "announcement", entity.getId(), null, Map.of(), ip);
        return toAnnouncement(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.AdminAuditPage listAudit(
            UUID actorId, UUID filterActorId, String action, String targetType, UUID targetId, CursorPage.Params page) {
        platformAdminGuard.requirePlatformAdmin(actorId);
        List<ru.posts.persistence.entity.AdminAuditLogEntity> all = adminAuditLogRepository.findFiltered(
                filterActorId, blankToNull(action), blankToNull(targetType), targetId);
        CursorPages.Slice<ru.posts.persistence.entity.AdminAuditLogEntity> slice = CursorPages.slice(
                all, page.cursor(), page.limitOrDefault(), ru.posts.persistence.entity.AdminAuditLogEntity::getId);
        List<AdminDtos.AuditEntry> items = new ArrayList<>();
        for (var entry : slice.items()) {
            String email = userRepository
                    .findById(entry.getActorUserId())
                    .map(UserEntity::getEmail)
                    .orElse(null);
            items.add(new AdminDtos.AuditEntry(
                    entry.getId(),
                    entry.getActorUserId(),
                    email,
                    entry.getAction(),
                    entry.getTargetType(),
                    entry.getTargetId(),
                    entry.getReason(),
                    entry.getDetails(),
                    entry.getIp(),
                    entry.getCreatedAt()));
        }
        return new CursorPage.AdminAuditPage(items, slice.nextCursor(), slice.hasMore());
    }

    private AdminDtos.WorkspaceCard toWorkspaceCard(WorkspaceEntity ws) {
        UserEntity owner = userRepository.findById(ws.getOwnerUserId()).orElse(null);
        PlanEntity plan = ws.getPlanId() == null
                ? null
                : planRepository.findById(ws.getPlanId()).orElse(null);
        List<AdminDtos.ChannelBrief> channels =
                channelRepository.findByWorkspaceIdAndDeletedAtIsNull(ws.getId()).stream()
                        .map(c -> new AdminDtos.ChannelBrief(
                                c.getId(), c.getNetwork(), c.getTitle(), c.getStatus(), c.getUsername()))
                        .toList();
        List<PublicationEntity> failures = publicationRepository.findRecentFailures(ws.getId());
        List<AdminDtos.PublicationErrorBrief> errors = failures.stream()
                .limit(20)
                .map(p -> new AdminDtos.PublicationErrorBrief(
                        p.getId(),
                        p.getNetwork(),
                        p.getState(),
                        p.getErrorCode(),
                        p.getErrorMessage(),
                        p.getCreatedAt()))
                .toList();
        BigDecimal balance = ledgerEntryRepository.findByWorkspaceIdOrderByCreatedAtDesc(ws.getId()).stream()
                .map(LedgerEntryEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AdminDtos.WorkspaceCard(
                ws.getId(),
                ws.getName(),
                ws.getOwnerUserId(),
                owner == null ? null : owner.getEmail(),
                plan == null ? null : plan.getCode(),
                plan == null ? Map.of() : plan.getLimits(),
                ApiEnums.BillingStatus.valueOf(ws.getBillingStatus()),
                ws.getTrialEndsAt(),
                ws.getCurrentPeriodEnd(),
                balance,
                channels,
                errors,
                ws.getCreatedAt());
    }

    private WorkspaceEntity requireWorkspace(UUID workspaceId) {
        return workspaceRepository
                .findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Workspace not found"));
    }

    private BillingDtos.Plan toPlan(PlanEntity plan) {
        return new BillingDtos.Plan(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getPriceMonth(),
                plan.getPriceYear(),
                plan.getLimits());
    }

    private AdminDtos.PromoCode toPromo(PromoCodeEntity promo) {
        return new AdminDtos.PromoCode(
                promo.getId(),
                promo.getCode(),
                promo.getKind(),
                promo.getValue(),
                promo.getValidFrom(),
                promo.getValidUntil(),
                promo.getMaxRedemptions(),
                promo.getRedemptionCount(),
                promo.isStackable(),
                promo.isOncePerWorkspace(),
                promo.isActive(),
                promo.getCreatedAt());
    }

    private AdminDtos.FeatureFlag toFlag(FeatureFlagEntity flag) {
        return new AdminDtos.FeatureFlag(
                flag.getId(),
                flag.getKey(),
                flag.getDescription(),
                flag.isEnabledGlobal(),
                flag.getCreatedAt(),
                flag.getUpdatedAt());
    }

    private AdminDtos.Announcement toAnnouncement(AnnouncementEntity entity) {
        return new AdminDtos.Announcement(
                entity.getId(),
                entity.getTitle(),
                entity.getBody(),
                entity.getSeverity(),
                entity.isActive(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.getCreatedBy(),
                entity.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
