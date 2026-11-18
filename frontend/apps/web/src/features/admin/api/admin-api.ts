import type {
  AdminAnnouncement,
  AdminAnnouncementCreateRequest,
  AdminAnnouncementPage,
  AdminAnnouncementUpdateRequest,
  AdminAuditPage,
  AdminFeatureFlag,
  AdminFeatureFlagUpsertRequest,
  AdminImpersonateRequest,
  AdminImpersonateResponse,
  AdminPromoCode,
  AdminPromoCreateRequest,
  AdminPromoPage,
  AdminPromoUpdateRequest,
  AdminQueueInfo,
  AdminQueueMessageActionResponse,
  AdminQueueMessagePage,
  AdminUserCard,
  AdminUserPage,
  AdminWorkspaceCard,
  AdminWorkspaceFeatureFlag,
  AdminWorkspaceFeatureFlagRequest,
  AdminWorkspacePage,
  AdminAdjustBalanceRequest,
  AdminAdjustSubscriptionRequest,
  Plan,
  TokenResponse,
} from '@posts/api-client';

import {
  applyTokenResponse,
  clearImpersonationTarget,
  getApiClient,
  setImpersonationTarget,
  unwrapApi,
} from '@/shared/api-client';

function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export async function searchAdminUsers(params: {
  q?: string;
  platformAdmin?: boolean;
  cursor?: string;
  limit?: number;
}): Promise<AdminUserPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/admin/users', {
      params: { query: params },
    }),
  );
}

export async function getAdminUser(userId: string): Promise<AdminUserCard> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/admin/users/{userId}', {
      params: { path: { userId } },
    }),
  );
}

export async function impersonateUser(
  userId: string,
  body: AdminImpersonateRequest,
): Promise<AdminImpersonateResponse> {
  const client = getApiClient();
  const response = unwrapApi(
    await client.POST('/api/v1/admin/users/{userId}/impersonate', {
      params: { path: { userId } },
      body,
    }),
  );
  if (response.accessToken) {
    applyTokenResponse({ accessToken: response.accessToken } as TokenResponse);
  }
  if (response.targetUserId && response.targetEmail) {
    setImpersonationTarget({
      userId: response.targetUserId,
      email: response.targetEmail,
      reason: response.reason ?? body.reason,
    });
  }
  return response;
}

/** Exit impersonation by refreshing the admin session (refresh cookie stays the actor's). */
export async function exitImpersonation(): Promise<void> {
  const client = getApiClient();
  const tokens = unwrapApi(await client.POST('/api/v1/auth/refresh', { body: {} }));
  applyTokenResponse(tokens);
  clearImpersonationTarget();
}

export async function searchAdminWorkspaces(params: {
  q?: string;
  planCode?: string;
  billingStatus?: string;
  cursor?: string;
  limit?: number;
}): Promise<AdminWorkspacePage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/admin/workspaces', {
      params: { query: params },
    }),
  );
}

export async function getAdminWorkspace(workspaceId: string): Promise<AdminWorkspaceCard> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/admin/workspaces/{workspaceId}', {
      params: { path: { workspaceId } },
    }),
  );
}

export async function adjustWorkspaceSubscription(
  workspaceId: string,
  body: AdminAdjustSubscriptionRequest,
): Promise<AdminWorkspaceCard> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/admin/workspaces/{workspaceId}/subscription', {
      params: { path: { workspaceId } },
      body,
    }),
  );
}

export async function adjustWorkspaceBalance(
  workspaceId: string,
  body: AdminAdjustBalanceRequest,
): Promise<AdminWorkspaceCard> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/admin/workspaces/{workspaceId}/balance', {
      params: { path: { workspaceId } },
      body,
    }),
  );
}

export async function setWorkspaceFeatureFlag(
  workspaceId: string,
  flagKey: string,
  body: AdminWorkspaceFeatureFlagRequest,
): Promise<AdminWorkspaceFeatureFlag> {
  const client = getApiClient();
  return unwrapApi(
    await client.PUT('/api/v1/admin/workspaces/{workspaceId}/feature-flags/{flagKey}', {
      params: { path: { workspaceId, flagKey } },
      body,
    }),
  );
}

export async function listAdminPlans(): Promise<Plan[]> {
  const client = getApiClient();
  return unwrapApi(await client.GET('/api/v1/admin/plans'));
}

export async function upsertAdminPlan(body: {
  code: string;
  name: string;
  priceMonth: number;
  priceYear: number;
  limits: Record<string, number | null>;
}): Promise<Plan> {
  const client = getApiClient();
  return unwrapApi(
    await client.PUT('/api/v1/admin/plans', {
      // OpenAPI codegen maps map values to Record<string, never>; runtime accepts numbers/null.
      body: body as never,
    }),
  );
}

export async function listAdminPromos(params?: {
  cursor?: string;
  limit?: number;
}): Promise<AdminPromoPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/admin/promos', {
      params: { query: params },
    }),
  );
}

export async function createAdminPromo(body: AdminPromoCreateRequest): Promise<AdminPromoCode> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/admin/promos', { body }));
}

export async function updateAdminPromo(
  promoId: string,
  body: AdminPromoUpdateRequest,
): Promise<AdminPromoCode> {
  const client = getApiClient();
  return unwrapApi(
    await client.PUT('/api/v1/admin/promos/{promoId}', {
      params: { path: { promoId } },
      body,
    }),
  );
}

export async function listAdminQueues(): Promise<AdminQueueInfo[]> {
  const client = getApiClient();
  return unwrapApi(await client.GET('/api/v1/admin/queues'));
}

export async function listAdminQueueMessages(
  queue: string,
  params?: { cursor?: string; limit?: number },
): Promise<AdminQueueMessagePage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/admin/queues/{queue}/messages', {
      params: { path: { queue }, query: params },
    }),
  );
}

export async function retryAdminQueueMessage(
  queue: string,
  messageId: string,
): Promise<AdminQueueMessageActionResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/admin/queues/{queue}/messages/{messageId}/retry', {
      params: {
        path: { queue, messageId },
        header: { 'Idempotency-Key': newIdempotencyKey() },
      },
    }),
  );
}

export async function deleteAdminQueueMessage(
  queue: string,
  messageId: string,
): Promise<AdminQueueMessageActionResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/admin/queues/{queue}/messages/{messageId}/delete', {
      params: {
        path: { queue, messageId },
        header: { 'Idempotency-Key': newIdempotencyKey() },
      },
    }),
  );
}

export async function listAdminFeatureFlags(): Promise<AdminFeatureFlag[]> {
  const client = getApiClient();
  return unwrapApi(await client.GET('/api/v1/admin/feature-flags'));
}

export async function upsertAdminFeatureFlag(
  body: AdminFeatureFlagUpsertRequest,
): Promise<AdminFeatureFlag> {
  const client = getApiClient();
  return unwrapApi(await client.PUT('/api/v1/admin/feature-flags', { body }));
}

export async function listAdminAnnouncements(params?: {
  cursor?: string;
  limit?: number;
}): Promise<AdminAnnouncementPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/admin/announcements', {
      params: { query: params },
    }),
  );
}

export async function createAdminAnnouncement(
  body: AdminAnnouncementCreateRequest,
): Promise<AdminAnnouncement> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/admin/announcements', { body }));
}

export async function updateAdminAnnouncement(
  announcementId: string,
  body: AdminAnnouncementUpdateRequest,
): Promise<AdminAnnouncement> {
  const client = getApiClient();
  return unwrapApi(
    await client.PUT('/api/v1/admin/announcements/{announcementId}', {
      params: { path: { announcementId } },
      body,
    }),
  );
}

export async function listAdminAudit(params?: {
  actorUserId?: string;
  action?: string;
  targetType?: string;
  targetId?: string;
  cursor?: string;
  limit?: number;
}): Promise<AdminAuditPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/admin/audit', {
      params: { query: params },
    }),
  );
}
