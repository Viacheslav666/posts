package ru.posts.api.config;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.admin.AdminService;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.web.dto.admin.AdminDtos;
import ru.posts.api.web.dto.billing.BillingDtos;
import ru.posts.api.web.dto.common.CursorPage;

@Configuration
@Profile("openapi")
public class OpenApiAdminStubConfig {

    @Bean
    AdminService adminService() {
        return new AdminService() {
            @Override
            public CursorPage.AdminUserPage searchUsers(
                    UUID actorId, String q, Boolean platformAdmin, CursorPage.Params page) {
                return new CursorPage.AdminUserPage(List.of(), null, false);
            }

            @Override
            public AdminDtos.UserCard getUser(UUID actorId, UUID userId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public CursorPage.AdminWorkspacePage searchWorkspaces(
                    UUID actorId, String q, String billingStatus, String planCode, CursorPage.Params page) {
                return new CursorPage.AdminWorkspacePage(List.of(), null, false);
            }

            @Override
            public AdminDtos.WorkspaceCard getWorkspace(UUID actorId, UUID workspaceId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AdminDtos.ImpersonateResponse impersonate(
                    UUID actorId,
                    UUID targetUserId,
                    AdminDtos.ImpersonateRequest request,
                    UUID actorSessionId,
                    String ip) {
                return new AdminDtos.ImpersonateResponse(
                        "eyJhbGciOiJFUzI1NiJ9.impersonation",
                        1800,
                        Instant.parse("2026-11-18T12:00:00Z"),
                        targetUserId,
                        "user@example.com",
                        request.reason(),
                        true);
            }

            @Override
            public List<BillingDtos.Plan> listPlans(UUID actorId) {
                return List.of();
            }

            @Override
            public BillingDtos.Plan upsertPlan(UUID actorId, AdminDtos.PlanUpsertRequest request, String ip) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public CursorPage.AdminPromoPage listPromos(UUID actorId, CursorPage.Params page) {
                return new CursorPage.AdminPromoPage(List.of(), null, false);
            }

            @Override
            public AdminDtos.PromoCode createPromo(UUID actorId, AdminDtos.PromoCreateRequest request, String ip) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AdminDtos.PromoCode updatePromo(
                    UUID actorId, UUID promoId, AdminDtos.PromoUpdateRequest request, String ip) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AdminDtos.WorkspaceCard adjustSubscription(
                    UUID actorId, UUID workspaceId, AdminDtos.AdjustSubscriptionRequest request, String ip) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AdminDtos.LedgerEntry adjustBalance(
                    UUID actorId, UUID workspaceId, AdminDtos.AdjustBalanceRequest request, String ip) {
                return new AdminDtos.LedgerEntry(
                        UUID.randomUUID(),
                        workspaceId,
                        request.amount(),
                        "admin_adjustment",
                        request.reason(),
                        Instant.now());
            }

            @Override
            public List<AdminDtos.QueueInfo> listQueues(UUID actorId) {
                return List.of(new AdminDtos.QueueInfo("publish.tg.dlq", true, 0, 0));
            }

            @Override
            public CursorPage.AdminQueueMessagePage listQueueMessages(
                    UUID actorId, String queue, CursorPage.Params page) {
                return new CursorPage.AdminQueueMessagePage(List.of(), null, false);
            }

            @Override
            public AdminDtos.QueueMessageActionResponse retryQueueMessage(
                    UUID actorId, String queue, String messageId, String idempotencyKey, String ip) {
                return new AdminDtos.QueueMessageActionResponse(queue, messageId, "retry", true, "requeued");
            }

            @Override
            public AdminDtos.QueueMessageActionResponse deleteQueueMessage(
                    UUID actorId, String queue, String messageId, String idempotencyKey, String ip) {
                return new AdminDtos.QueueMessageActionResponse(queue, messageId, "delete", true, "deleted");
            }

            @Override
            public List<AdminDtos.FeatureFlag> listFeatureFlags(UUID actorId) {
                return List.of();
            }

            @Override
            public AdminDtos.FeatureFlag upsertFeatureFlag(
                    UUID actorId, AdminDtos.FeatureFlagUpsertRequest request, String ip) {
                return new AdminDtos.FeatureFlag(
                        UUID.randomUUID(),
                        request.key(),
                        request.description(),
                        request.enabledGlobal(),
                        Instant.now(),
                        Instant.now());
            }

            @Override
            public AdminDtos.WorkspaceFeatureFlag setWorkspaceFeatureFlag(
                    UUID actorId,
                    UUID workspaceId,
                    String flagKey,
                    AdminDtos.WorkspaceFeatureFlagRequest request,
                    String ip) {
                return new AdminDtos.WorkspaceFeatureFlag(
                        workspaceId, flagKey, Boolean.TRUE.equals(request.enabled()), Instant.now());
            }

            @Override
            public CursorPage.AdminAnnouncementPage listAnnouncements(UUID actorId, CursorPage.Params page) {
                return new CursorPage.AdminAnnouncementPage(List.of(), null, false);
            }

            @Override
            public AdminDtos.Announcement createAnnouncement(
                    UUID actorId, AdminDtos.AnnouncementCreateRequest request, String ip) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AdminDtos.Announcement updateAnnouncement(
                    UUID actorId, UUID announcementId, AdminDtos.AnnouncementUpdateRequest request, String ip) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public CursorPage.AdminAuditPage listAudit(
                    UUID actorId,
                    UUID filterActorId,
                    String action,
                    String targetType,
                    UUID targetId,
                    CursorPage.Params page) {
                return new CursorPage.AdminAuditPage(List.of(), null, false);
            }
        };
    }
}
