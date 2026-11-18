package ru.posts.api.admin;

import java.util.List;
import java.util.UUID;
import ru.posts.api.web.dto.admin.AdminDtos;
import ru.posts.api.web.dto.billing.BillingDtos;
import ru.posts.api.web.dto.common.CursorPage;

public interface AdminService {

    CursorPage.AdminUserPage searchUsers(UUID actorId, String q, Boolean platformAdmin, CursorPage.Params page);

    AdminDtos.UserCard getUser(UUID actorId, UUID userId);

    CursorPage.AdminWorkspacePage searchWorkspaces(
            UUID actorId, String q, String billingStatus, String planCode, CursorPage.Params page);

    AdminDtos.WorkspaceCard getWorkspace(UUID actorId, UUID workspaceId);

    AdminDtos.ImpersonateResponse impersonate(
            UUID actorId, UUID targetUserId, AdminDtos.ImpersonateRequest request, UUID actorSessionId, String ip);

    List<BillingDtos.Plan> listPlans(UUID actorId);

    BillingDtos.Plan upsertPlan(UUID actorId, AdminDtos.PlanUpsertRequest request, String ip);

    CursorPage.AdminPromoPage listPromos(UUID actorId, CursorPage.Params page);

    AdminDtos.PromoCode createPromo(UUID actorId, AdminDtos.PromoCreateRequest request, String ip);

    AdminDtos.PromoCode updatePromo(UUID actorId, UUID promoId, AdminDtos.PromoUpdateRequest request, String ip);

    AdminDtos.WorkspaceCard adjustSubscription(
            UUID actorId, UUID workspaceId, AdminDtos.AdjustSubscriptionRequest request, String ip);

    AdminDtos.LedgerEntry adjustBalance(
            UUID actorId, UUID workspaceId, AdminDtos.AdjustBalanceRequest request, String ip);

    List<AdminDtos.QueueInfo> listQueues(UUID actorId);

    CursorPage.AdminQueueMessagePage listQueueMessages(UUID actorId, String queue, CursorPage.Params page);

    AdminDtos.QueueMessageActionResponse retryQueueMessage(
            UUID actorId, String queue, String messageId, String idempotencyKey, String ip);

    AdminDtos.QueueMessageActionResponse deleteQueueMessage(
            UUID actorId, String queue, String messageId, String idempotencyKey, String ip);

    List<AdminDtos.FeatureFlag> listFeatureFlags(UUID actorId);

    AdminDtos.FeatureFlag upsertFeatureFlag(UUID actorId, AdminDtos.FeatureFlagUpsertRequest request, String ip);

    AdminDtos.WorkspaceFeatureFlag setWorkspaceFeatureFlag(
            UUID actorId, UUID workspaceId, String flagKey, AdminDtos.WorkspaceFeatureFlagRequest request, String ip);

    CursorPage.AdminAnnouncementPage listAnnouncements(UUID actorId, CursorPage.Params page);

    AdminDtos.Announcement createAnnouncement(UUID actorId, AdminDtos.AnnouncementCreateRequest request, String ip);

    AdminDtos.Announcement updateAnnouncement(
            UUID actorId, UUID announcementId, AdminDtos.AnnouncementUpdateRequest request, String ip);

    CursorPage.AdminAuditPage listAudit(
            UUID actorId, UUID filterActorId, String action, String targetType, UUID targetId, CursorPage.Params page);
}
