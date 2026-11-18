import type {
  AdminAnnouncement,
  AdminAnnouncementCreateRequest,
  AdminAnnouncementUpdateRequest,
  AdminAuditEntry,
  AdminFeatureFlag,
  AdminFeatureFlagUpsertRequest,
  AdminImpersonateRequest,
  AdminImpersonateResponse,
  AdminPromoCode,
  AdminPromoCreateRequest,
  AdminPromoUpdateRequest,
  AdminQueueInfo,
  AdminQueueMessage,
  AdminQueueMessageActionResponse,
  AdminUserCard,
  AdminUserSummary,
  AdminWorkspaceCard,
  AdminWorkspaceFeatureFlag,
  AdminWorkspaceSummary,
  Plan,
  UserProfile,
} from '@posts/api-client';

import { listPlans, upsertPlanLimits } from './billing-store';
import {
  ACME_WORKSPACE_ID,
  DEMO_USER_ID,
  PERSONAL_WORKSPACE_ID,
  SECONDARY_USER_ID,
  getDemoProfile,
  getWorkspace,
  listWorkspaces,
} from './workspace-store';

const IMPERSONATION_TOKEN_PREFIX = 'msw.access.impersonating.';

export type AdminStoreState = {
  users: AdminUserSummary[];
  userCards: Record<string, AdminUserCard>;
  workspaces: AdminWorkspaceSummary[];
  workspaceCards: Record<string, AdminWorkspaceCard>;
  featureFlags: AdminFeatureFlag[];
  workspaceFlags: Record<string, Record<string, boolean>>;
  promos: AdminPromoCode[];
  announcements: AdminAnnouncement[];
  queues: AdminQueueInfo[];
  queueMessages: Record<string, AdminQueueMessage[]>;
  audit: AdminAuditEntry[];
  impersonation: {
    active: boolean;
    actorUserId: string;
    targetUserId: string;
    targetEmail: string;
    reason: string;
    expiresAt: string;
  } | null;
  platformAdmin: boolean;
};

function isoNow(): string {
  return new Date().toISOString();
}

function nextId(suffix: string): string {
  return `019a0000-admn-7000-8000-${suffix.padStart(12, '0')}`;
}

let idCounter = 200;

function createInitialState(): AdminStoreState {
  const now = isoNow();
  const demoCard: AdminUserCard = {
    id: DEMO_USER_ID,
    email: 'demo@posts.app',
    name: 'Demo User',
    emailVerified: true,
    totpEnabled: false,
    platformAdmin: true,
    createdAt: now,
    workspaces: [
      {
        workspaceId: PERSONAL_WORKSPACE_ID,
        workspaceName: 'Личное пространство',
        role: 'owner',
        billingStatus: 'trial',
      },
      {
        workspaceId: ACME_WORKSPACE_ID,
        workspaceName: 'Acme Brand',
        role: 'owner',
        billingStatus: 'active',
      },
    ],
  };
  const secondaryCard: AdminUserCard = {
    id: SECONDARY_USER_ID,
    email: 'editor@posts.app',
    name: 'Editor User',
    emailVerified: true,
    totpEnabled: false,
    platformAdmin: false,
    createdAt: now,
    workspaces: [
      {
        workspaceId: PERSONAL_WORKSPACE_ID,
        workspaceName: 'Личное пространство',
        role: 'editor',
        billingStatus: 'trial',
      },
    ],
  };

  const personal = getWorkspace(PERSONAL_WORKSPACE_ID);
  const acme = getWorkspace(ACME_WORKSPACE_ID);

  const workspaceCards: Record<string, AdminWorkspaceCard> = {
    [PERSONAL_WORKSPACE_ID]: {
      id: PERSONAL_WORKSPACE_ID,
      name: personal?.name ?? 'Личное пространство',
      ownerUserId: DEMO_USER_ID,
      ownerEmail: 'demo@posts.app',
      planCode: personal?.planCode ?? 'free',
      billingStatus: 'trial',
      balance: 0,
      createdAt: personal?.createdAt ?? now,
      channels: [],
      recentPublicationErrors: [],
      planLimits: { channels: 3, seats: 1 } as unknown as AdminWorkspaceCard['planLimits'],
    },
    [ACME_WORKSPACE_ID]: {
      id: ACME_WORKSPACE_ID,
      name: acme?.name ?? 'Acme Brand',
      ownerUserId: DEMO_USER_ID,
      ownerEmail: 'demo@posts.app',
      planCode: acme?.planCode ?? 'pro',
      billingStatus: 'active',
      balance: 1500,
      createdAt: acme?.createdAt ?? now,
      channels: [],
      recentPublicationErrors: [
        {
          publicationId: nextId('err1'),
          network: 'vk',
          state: 'failed',
          errorCode: 'vk_token_expired',
          errorMessage: 'Token expired',
          createdAt: now,
        },
      ],
      planLimits: { channels: 30, seats: 3 } as unknown as AdminWorkspaceCard['planLimits'],
    },
  };

  const dlqName = 'publish.dlq';
  const mainQueue = 'publish';

  return {
    platformAdmin: true,
    impersonation: null,
    users: [
      {
        id: demoCard.id,
        email: demoCard.email,
        name: demoCard.name,
        emailVerified: true,
        totpEnabled: false,
        platformAdmin: true,
        workspaceCount: 2,
        createdAt: now,
      },
      {
        id: secondaryCard.id,
        email: secondaryCard.email,
        name: secondaryCard.name,
        emailVerified: true,
        totpEnabled: false,
        platformAdmin: false,
        workspaceCount: 1,
        createdAt: now,
      },
    ],
    userCards: {
      [DEMO_USER_ID]: demoCard,
      [SECONDARY_USER_ID]: secondaryCard,
    },
    workspaces: Object.values(workspaceCards).map((ws) => ({
      id: ws.id,
      name: ws.name,
      ownerUserId: ws.ownerUserId,
      ownerEmail: ws.ownerEmail,
      planCode: ws.planCode,
      billingStatus: ws.billingStatus,
      channelCount: ws.channels?.length ?? 0,
      createdAt: ws.createdAt,
    })),
    workspaceCards,
    featureFlags: [
      {
        id: nextId('ff01'),
        key: 'stories.enabled',
        description: 'Scheduled stories UI',
        enabledGlobal: true,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: nextId('ff02'),
        key: 'grabber.enabled',
        description: 'Grabber / RSS ingest',
        enabledGlobal: true,
        createdAt: now,
        updatedAt: now,
      },
    ],
    workspaceFlags: {},
    promos: [
      {
        id: nextId('pr01'),
        code: 'WELCOME10',
        kind: 'percent',
        value: 10,
        active: true,
        maxRedemptions: 100,
        redemptionCount: 3,
        oncePerWorkspace: true,
        stackable: false,
        validFrom: now,
        validUntil: new Date(Date.now() + 90 * 86400000).toISOString(),
        createdAt: now,
      },
    ],
    announcements: [
      {
        id: nextId('an01'),
        title: 'Плановые работы',
        body: 'В субботу с 02:00 до 04:00 MSK возможны краткие перерывы.',
        severity: 'info',
        active: true,
        startsAt: now,
        createdAt: now,
        createdBy: DEMO_USER_ID,
      },
    ],
    queues: [
      { name: mainQueue, messageCount: 2, consumerCount: 1, dlq: false },
      { name: dlqName, messageCount: 1, consumerCount: 0, dlq: true },
    ],
    queueMessages: {
      [mainQueue]: [
        {
          messageId: nextId('qm01'),
          payload: '{"type":"publish","publicationId":"pub-1"}',
          enqueuedAt: now,
          redeliveredCount: 0,
        },
      ],
      [dlqName]: [
        {
          messageId: nextId('qm02'),
          payload: '{"type":"publish","publicationId":"pub-fail"}',
          enqueuedAt: now,
          redeliveredCount: 5,
          headers: { 'x-exception-message': 'vk_api_error' } as unknown as AdminQueueMessage['headers'],
        },
      ],
    },
    audit: [
      {
        id: nextId('au01'),
        actorUserId: DEMO_USER_ID,
        actorEmail: 'demo@posts.app',
        action: 'admin.feature_flags.upsert',
        targetType: 'feature_flag',
        targetId: nextId('ff01'),
        reason: 'enable stories',
        createdAt: now,
        ip: '127.0.0.1',
      },
    ],
  };
}

let state = createInitialState();

export function resetAdminStore(): void {
  idCounter = 200;
  state = createInitialState();
}

export function isImpersonationToken(token: string): boolean {
  return token.startsWith(IMPERSONATION_TOKEN_PREFIX);
}

export function getImpersonationTargetId(token: string): string | null {
  if (!isImpersonationToken(token)) return null;
  return token.slice(IMPERSONATION_TOKEN_PREFIX.length) || null;
}

export function requirePlatformAdmin(request: Request): boolean {
  const auth = request.headers.get('Authorization') ?? '';
  const token = auth.replace(/^Bearer\s+/i, '');
  if (isImpersonationToken(token)) {
    return false;
  }
  return state.platformAdmin;
}

export function getAdminMeOverlay(profile: UserProfile, request: Request): UserProfile {
  const auth = request.headers.get('Authorization') ?? '';
  const token = auth.replace(/^Bearer\s+/i, '');
  if (isImpersonationToken(token) && state.impersonation) {
    const target = state.userCards[state.impersonation.targetUserId];
    return {
      ...profile,
      id: state.impersonation.targetUserId,
      email: state.impersonation.targetEmail,
      name: target?.name ?? state.impersonation.targetEmail,
      platformAdmin: target?.platformAdmin ?? false,
      impersonation: {
        active: true,
        actorUserId: state.impersonation.actorUserId,
        reason: state.impersonation.reason,
        expiresAt: state.impersonation.expiresAt,
      },
    };
  }
  return {
    ...profile,
    platformAdmin: state.platformAdmin,
    impersonation: undefined,
  };
}

function recordAudit(
  action: string,
  targetType: string,
  targetId: string | undefined,
  reason?: string,
): void {
  state.audit = [
    {
      id: nextId(`au${++idCounter}`),
      actorUserId: DEMO_USER_ID,
      actorEmail: 'demo@posts.app',
      action,
      targetType,
      targetId,
      reason,
      createdAt: isoNow(),
      ip: '127.0.0.1',
    },
    ...state.audit,
  ];
}

function paginate<T>(items: T[], cursor?: string | null, limit = 20) {
  const start = cursor ? Number.parseInt(cursor, 10) || 0 : 0;
  const slice = items.slice(start, start + limit);
  const next = start + limit;
  const hasMore = next < items.length;
  return {
    items: slice,
    hasMore,
    nextCursor: hasMore ? String(next) : undefined,
  };
}

export function searchUsers(params: {
  q?: string | null;
  platformAdmin?: string | null;
  cursor?: string | null;
  limit?: string | null;
}) {
  let items = [...state.users];
  const q = params.q?.trim().toLowerCase();
  if (q) {
    items = items.filter(
      (u) =>
        u.email?.toLowerCase().includes(q) ||
        u.name?.toLowerCase().includes(q) ||
        u.id?.includes(q),
    );
  }
  if (params.platformAdmin === 'true') {
    items = items.filter((u) => u.platformAdmin);
  } else if (params.platformAdmin === 'false') {
    items = items.filter((u) => !u.platformAdmin);
  }
  const limit = Math.min(100, Math.max(1, Number(params.limit) || 20));
  return paginate(items, params.cursor, limit);
}

export function getUserCard(userId: string): AdminUserCard | undefined {
  return state.userCards[userId];
}

export function impersonate(
  userId: string,
  body: AdminImpersonateRequest,
): AdminImpersonateResponse | { error: string; status: number; code: string } {
  if (userId === DEMO_USER_ID) {
    return { error: 'Cannot impersonate yourself', status: 400, code: 'validation_failed' };
  }
  const target = state.userCards[userId];
  if (!target) {
    return { error: 'User not found', status: 404, code: 'not_found' };
  }
  const expiresAt = new Date(Date.now() + 15 * 60_000).toISOString();
  state.impersonation = {
    active: true,
    actorUserId: DEMO_USER_ID,
    targetUserId: userId,
    targetEmail: target.email ?? 'unknown',
    reason: body.reason,
    expiresAt,
  };
  recordAudit('admin.impersonate', 'user', userId, body.reason);
  return {
    accessToken: `${IMPERSONATION_TOKEN_PREFIX}${userId}`,
    expiresIn: 900,
    expiresAt,
    targetUserId: userId,
    targetEmail: target.email,
    reason: body.reason,
    impersonating: true,
  };
}

export function clearImpersonation(): void {
  state.impersonation = null;
}

export function searchWorkspaces(params: {
  q?: string | null;
  planCode?: string | null;
  billingStatus?: string | null;
  cursor?: string | null;
  limit?: string | null;
}) {
  // Keep summaries in sync with workspace store names/plans when possible.
  const live = listWorkspaces();
  state.workspaces = state.workspaces.map((ws) => {
    const liveWs = live.find((item) => item.id === ws.id);
    return liveWs
      ? { ...ws, name: liveWs.name, planCode: liveWs.planCode ?? ws.planCode }
      : ws;
  });

  let items = [...state.workspaces];
  const q = params.q?.trim().toLowerCase();
  if (q) {
    items = items.filter(
      (ws) =>
        ws.name?.toLowerCase().includes(q) ||
        ws.ownerEmail?.toLowerCase().includes(q) ||
        ws.id?.includes(q),
    );
  }
  if (params.planCode) {
    items = items.filter((ws) => ws.planCode === params.planCode);
  }
  if (params.billingStatus) {
    items = items.filter((ws) => ws.billingStatus === params.billingStatus);
  }
  const limit = Math.min(100, Math.max(1, Number(params.limit) || 20));
  return paginate(items, params.cursor, limit);
}

export function getWorkspaceCard(workspaceId: string): AdminWorkspaceCard | undefined {
  return state.workspaceCards[workspaceId];
}

export function adjustSubscription(
  workspaceId: string,
  body: {
    planCode: string;
    reason: string;
    billingStatus?: AdminWorkspaceCard['billingStatus'];
    periodEnd?: string;
    trialEndsAt?: string;
  },
): AdminWorkspaceCard | undefined {
  const card = state.workspaceCards[workspaceId];
  if (!card) return undefined;
  card.planCode = body.planCode;
  if (body.billingStatus) card.billingStatus = body.billingStatus;
  if (body.periodEnd) card.currentPeriodEnd = body.periodEnd;
  if (body.trialEndsAt) card.trialEndsAt = body.trialEndsAt;
  const summary = state.workspaces.find((ws) => ws.id === workspaceId);
  if (summary) {
    summary.planCode = body.planCode;
    if (body.billingStatus) summary.billingStatus = body.billingStatus;
  }
  recordAudit('admin.workspaces.adjust_subscription', 'workspace', workspaceId, body.reason);
  return card;
}

export function adjustBalance(
  workspaceId: string,
  body: { amount: number; reason: string; kind?: string },
): AdminWorkspaceCard | undefined {
  const card = state.workspaceCards[workspaceId];
  if (!card) return undefined;
  card.balance = (card.balance ?? 0) + body.amount;
  recordAudit('admin.workspaces.adjust_balance', 'workspace', workspaceId, body.reason);
  return card;
}

export function setWorkspaceFlag(
  workspaceId: string,
  flagKey: string,
  enabled: boolean,
): AdminWorkspaceFeatureFlag | undefined {
  if (!state.workspaceCards[workspaceId]) return undefined;
  state.workspaceFlags[workspaceId] = {
    ...(state.workspaceFlags[workspaceId] ?? {}),
    [flagKey]: enabled,
  };
  recordAudit('admin.workspaces.feature_flag', 'workspace', workspaceId, flagKey);
  return {
    workspaceId,
    flagKey,
    enabled,
    updatedAt: isoNow(),
  };
}

export function listAdminPlansFromStore(): Plan[] {
  return listPlans();
}

export function upsertAdminPlanInStore(body: {
  code: string;
  name: string;
  priceMonth: number;
  priceYear: number;
  limits: Record<string, number | null>;
}): Plan {
  const plan = upsertPlanLimits(body);
  recordAudit('admin.plan.upsert', 'plan', plan.id, body.code);
  return plan;
}

export function listPromos(params?: { cursor?: string | null; limit?: string | null }) {
  const limit = Math.min(100, Math.max(1, Number(params?.limit) || 20));
  return paginate(state.promos, params?.cursor, limit);
}

export function createPromo(body: AdminPromoCreateRequest): AdminPromoCode {
  const promo: AdminPromoCode = {
    id: nextId(`pr${++idCounter}`),
    code: body.code,
    kind: body.kind,
    value: body.value,
    active: body.active ?? true,
    maxRedemptions: body.maxRedemptions,
    redemptionCount: 0,
    oncePerWorkspace: body.oncePerWorkspace ?? true,
    stackable: body.stackable ?? false,
    validFrom: body.validFrom,
    validUntil: body.validUntil,
    createdAt: isoNow(),
  };
  state.promos = [promo, ...state.promos];
  recordAudit('admin.promos.create', 'promo', promo.id, body.code);
  return promo;
}

export function updatePromo(
  promoId: string,
  body: AdminPromoUpdateRequest,
): AdminPromoCode | undefined {
  const promo = state.promos.find((item) => item.id === promoId);
  if (!promo) return undefined;
  Object.assign(promo, body);
  recordAudit('admin.promos.update', 'promo', promoId);
  return promo;
}

export function listQueues(): AdminQueueInfo[] {
  return state.queues.map((q) => ({
    ...q,
    messageCount: state.queueMessages[q.name ?? '']?.length ?? q.messageCount ?? 0,
  }));
}

export function listQueueMessages(
  queue: string,
  params?: { cursor?: string | null; limit?: string | null },
) {
  const items = state.queueMessages[queue] ?? [];
  const limit = Math.min(100, Math.max(1, Number(params?.limit) || 20));
  return paginate(items, params?.cursor, limit);
}

export function retryQueueMessage(
  queue: string,
  messageId: string,
): AdminQueueMessageActionResponse | undefined {
  const messages = state.queueMessages[queue] ?? [];
  const idx = messages.findIndex((m) => m.messageId === messageId);
  if (idx < 0) return undefined;
  const [message] = messages.splice(idx, 1);
  const targetQueue = queue.endsWith('.dlq') ? queue.replace(/\.dlq$/, '') : queue;
  if (!state.queueMessages[targetQueue]) {
    state.queueMessages[targetQueue] = [];
  }
  state.queueMessages[targetQueue].push({
    ...message,
    redeliveredCount: (message.redeliveredCount ?? 0) + 1,
    enqueuedAt: isoNow(),
  });
  recordAudit('admin.queues.retry', 'queue_message', messageId, queue);
  return {
    action: 'retry',
    applied: true,
    messageId,
    queue,
    detail: `Requeued to ${targetQueue}`,
  };
}

export function deleteQueueMessage(
  queue: string,
  messageId: string,
): AdminQueueMessageActionResponse | undefined {
  const messages = state.queueMessages[queue] ?? [];
  const idx = messages.findIndex((m) => m.messageId === messageId);
  if (idx < 0) return undefined;
  messages.splice(idx, 1);
  recordAudit('admin.queues.delete', 'queue_message', messageId, queue);
  return {
    action: 'delete',
    applied: true,
    messageId,
    queue,
  };
}

export function listFeatureFlags(): AdminFeatureFlag[] {
  return [...state.featureFlags];
}

export function upsertFeatureFlag(body: AdminFeatureFlagUpsertRequest): AdminFeatureFlag {
  const existing = state.featureFlags.find((f) => f.key === body.key);
  if (existing) {
    existing.description = body.description ?? existing.description;
    existing.enabledGlobal = body.enabledGlobal ?? existing.enabledGlobal;
    existing.updatedAt = isoNow();
    recordAudit('admin.feature_flags.upsert', 'feature_flag', existing.id, body.key);
    return existing;
  }
  const created: AdminFeatureFlag = {
    id: nextId(`ff${++idCounter}`),
    key: body.key,
    description: body.description,
    enabledGlobal: body.enabledGlobal ?? false,
    createdAt: isoNow(),
    updatedAt: isoNow(),
  };
  state.featureFlags = [created, ...state.featureFlags];
  recordAudit('admin.feature_flags.upsert', 'feature_flag', created.id, body.key);
  return created;
}

export function listAnnouncements(params?: { cursor?: string | null; limit?: string | null }) {
  const limit = Math.min(100, Math.max(1, Number(params?.limit) || 20));
  return paginate(state.announcements, params?.cursor, limit);
}

export function createAnnouncement(body: AdminAnnouncementCreateRequest): AdminAnnouncement {
  const item: AdminAnnouncement = {
    id: nextId(`an${++idCounter}`),
    title: body.title,
    body: body.body,
    severity: body.severity,
    active: body.active ?? true,
    startsAt: body.startsAt ?? isoNow(),
    endsAt: body.endsAt,
    createdAt: isoNow(),
    createdBy: DEMO_USER_ID,
  };
  state.announcements = [item, ...state.announcements];
  recordAudit('admin.announcements.create', 'announcement', item.id, body.title);
  return item;
}

export function updateAnnouncement(
  announcementId: string,
  body: AdminAnnouncementUpdateRequest,
): AdminAnnouncement | undefined {
  const item = state.announcements.find((a) => a.id === announcementId);
  if (!item) return undefined;
  Object.assign(item, body);
  recordAudit('admin.announcements.update', 'announcement', announcementId);
  return item;
}

export function listAudit(params: {
  actorUserId?: string | null;
  action?: string | null;
  targetType?: string | null;
  targetId?: string | null;
  cursor?: string | null;
  limit?: string | null;
}) {
  let items = [...state.audit];
  if (params.actorUserId) {
    items = items.filter((a) => a.actorUserId === params.actorUserId);
  }
  if (params.action) {
    items = items.filter((a) => a.action === params.action);
  }
  if (params.targetType) {
    items = items.filter((a) => a.targetType === params.targetType);
  }
  if (params.targetId) {
    items = items.filter((a) => a.targetId === params.targetId);
  }
  const limit = Math.min(100, Math.max(1, Number(params.limit) || 20));
  return paginate(items, params.cursor, limit);
}

/** Used by refresh handler to restore admin after impersonation. */
export function endImpersonationOnRefresh(): void {
  clearImpersonation();
}

export function getDemoAdminProfile(request: Request): UserProfile {
  return getAdminMeOverlay(getDemoProfile(), request);
}
