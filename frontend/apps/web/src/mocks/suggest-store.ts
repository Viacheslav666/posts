/**
 * FE-22 / BE-34 MSW store for VK Suggest settings, moderation queue, bulk jobs.
 */
import type {
  EventSubscriptionResponse,
  ProblemDetail,
  SuggestBulkJob,
  SuggestBulkRequest,
  SuggestItem,
  SuggestItemApproveRequest,
  SuggestItemRejectRequest,
  SuggestSettings,
  SuggestSettingsCopyRequest,
  SuggestSettingsCopyResult,
  SuggestSettingsCreate,
  SuggestSettingsPatch,
  SuggestSyncResult,
} from '@posts/api-client';
import { http, HttpResponse, type HttpHandler } from 'msw';

import { withConfigDefaults, type SuggestConfig } from '@/features/suggest/model/config';
import { can, type WorkspaceRole } from '@/shared/lib/permissions';
import { INSUFFICIENT_PERMISSIONS_CODE } from '@/shared/lib/errors';

import { CHANNEL_IDS, getChannel } from './channel-store';
import { DEFAULT_PROJECT_ID, DEMO_USER_ID, PERSONAL_WORKSPACE_ID } from './workspace-store';

export const SETTINGS_IDS = {
  vkNews: '019a0000-sugg-7000-8000-000000000001',
} as const;

export const ITEM_IDS = {
  pending1: '019a0000-sitem-7000-8000-000000000001',
  pending2: '019a0000-sitem-7000-8000-000000000002',
  pending3: '019a0000-sitem-7000-8000-000000000003',
  published1: '019a0000-sitem-7000-8000-000000000004',
  rejected1: '019a0000-sitem-7000-8000-000000000005',
  deleted1: '019a0000-sitem-7000-8000-000000000006',
} as const;

type SuggestMutationError = {
  status: number;
  code: string;
  detail: string;
};

type StoreState = {
  settings: SuggestSettings[];
  items: SuggestItem[];
  jobs: SuggestBulkJob[];
  seq: number;
};

function nowIso(): string {
  return new Date().toISOString();
}

function minutesAgo(minutes: number): string {
  return new Date(Date.now() - minutes * 60_000).toISOString();
}

function nextId(prefix: string, seq: number): string {
  return `019a0000-${prefix}-7000-8000-${String(seq).padStart(12, '0')}`;
}

function defaultConfig(overrides?: Partial<SuggestConfig>): SuggestConfig {
  return {
    ...withConfigDefaults({}),
    ...overrides,
    filters: {
      ...withConfigDefaults({}).filters,
      ...(overrides?.filters ?? {}),
      stop_words: overrides?.filters?.stop_words ?? ['спам'],
      keyword_include: overrides?.filters?.keyword_include ?? [],
    },
  };
}

function createInitialState(): StoreState {
  const now = nowIso();
  const config = defaultConfig();
  return {
    seq: 100,
    settings: [
      {
        id: SETTINGS_IDS.vkNews,
        workspaceId: PERSONAL_WORKSPACE_ID,
        projectId: DEFAULT_PROJECT_ID,
        channelId: CHANNEL_IDS.vkNews,
        status: 'active',
        mode: 'manual',
        config: config as unknown as SuggestSettings['config'],
        configVersion: 1,
        version: 1,
        createdAt: minutesAgo(60 * 24),
        updatedAt: now,
        lastSyncAt: minutesAgo(30),
        nextSyncAt: new Date(Date.now() + 15 * 60_000).toISOString(),
      },
    ],
    items: [
      {
        id: ITEM_IDS.pending1,
        workspaceId: PERSONAL_WORKSPACE_ID,
        channelId: CHANNEL_IDS.vkNews,
        settingsId: SETTINGS_IDS.vkNews,
        vkPostId: '456001',
        authorVkId: '10001',
        text: 'Предложка: анонс мероприятия в субботу. Ждём вас!',
        attachments: [{ type: 'photo' }] as unknown as SuggestItem['attachments'],
        status: 'pending_moderation',
        reasons: ['ads_pause'],
        createdAt: minutesAgo(40),
        updatedAt: minutesAgo(40),
      },
      {
        id: ITEM_IDS.pending2,
        workspaceId: PERSONAL_WORKSPACE_ID,
        channelId: CHANNEL_IDS.vkNews,
        settingsId: SETTINGS_IDS.vkNews,
        vkPostId: '456002',
        authorVkId: '10002',
        text: 'Второй пост из предложки - вопрос про доставку.',
        attachments: [],
        status: 'pending_moderation',
        reasons: [],
        createdAt: minutesAgo(25),
        updatedAt: minutesAgo(25),
      },
      {
        id: ITEM_IDS.pending3,
        workspaceId: PERSONAL_WORKSPACE_ID,
        channelId: CHANNEL_IDS.vkNews,
        settingsId: SETTINGS_IDS.vkNews,
        vkPostId: '456003',
        authorVkId: '10003',
        text: 'Третий материал на модерации с вложением.',
        attachments: [{ type: 'doc' }] as unknown as SuggestItem['attachments'],
        status: 'pending_moderation',
        reasons: ['near_duplicate'],
        createdAt: minutesAgo(12),
        updatedAt: minutesAgo(12),
      },
      {
        id: ITEM_IDS.published1,
        workspaceId: PERSONAL_WORKSPACE_ID,
        channelId: CHANNEL_IDS.vkNews,
        settingsId: SETTINGS_IDS.vkNews,
        vkPostId: '455900',
        authorVkId: '10010',
        text: 'Уже опубликованный пост из предложки.',
        attachments: [],
        status: 'published',
        reasons: [],
        decidedBy: DEMO_USER_ID,
        decidedAt: minutesAgo(120),
        publishedRemotePostId: 'wall-1_100',
        createdAt: minutesAgo(180),
        updatedAt: minutesAgo(120),
      },
      {
        id: ITEM_IDS.rejected1,
        workspaceId: PERSONAL_WORKSPACE_ID,
        channelId: CHANNEL_IDS.vkNews,
        settingsId: SETTINGS_IDS.vkNews,
        vkPostId: '455800',
        authorVkId: '10011',
        text: 'Отклонённый пост со стоп-словом.',
        attachments: [],
        status: 'rejected',
        reasons: ['stop_words'],
        decidedBy: DEMO_USER_ID,
        decidedAt: minutesAgo(200),
        createdAt: minutesAgo(240),
        updatedAt: minutesAgo(200),
      },
      {
        id: ITEM_IDS.deleted1,
        workspaceId: PERSONAL_WORKSPACE_ID,
        channelId: CHANNEL_IDS.vkNews,
        settingsId: SETTINGS_IDS.vkNews,
        vkPostId: '455700',
        authorVkId: '10012',
        text: 'Удалённый удалённо пост.',
        attachments: [],
        status: 'deleted_remote',
        reasons: [],
        decidedBy: DEMO_USER_ID,
        decidedAt: minutesAgo(300),
        createdAt: minutesAgo(360),
        updatedAt: minutesAgo(300),
      },
    ],
    jobs: [],
  };
}

let state = createInitialState();

export function resetSuggestStore(): void {
  state = createInitialState();
}

export function getSuggestDemoSeed(): {
  workspaceId: string;
  channelIds: string[];
  eventConsumers: EventSubscriptionResponse['consumers'];
} {
  return {
    workspaceId: PERSONAL_WORKSPACE_ID,
    channelIds: [CHANNEL_IDS.vkNews],
    eventConsumers: ['suggest'],
  };
}

function findSettings(settingsId: string): SuggestSettings | undefined {
  return state.settings.find((item) => item.id === settingsId);
}

function findItem(itemId: string): SuggestItem | undefined {
  return state.items.find((item) => item.id === itemId);
}

function isMutationError(value: unknown): value is SuggestMutationError {
  return Boolean(value && typeof value === 'object' && 'code' in value && 'status' in value);
}

export function listSuggestSettings(params: {
  workspaceId: string;
  projectId: string;
}): SuggestSettings[] {
  return state.settings.filter(
    (item) => item.workspaceId === params.workspaceId && item.projectId === params.projectId,
  );
}

export function getSuggestSettings(settingsId: string): SuggestSettings | undefined {
  return findSettings(settingsId);
}

export function createSuggestSettings(
  workspaceId: string,
  projectId: string,
  body: SuggestSettingsCreate,
): SuggestSettings | SuggestMutationError {
  const channel = getChannel(body.channelId);
  if (!channel || channel.workspaceId !== workspaceId) {
    return { status: 404, code: 'not_found', detail: 'Channel not found' };
  }
  if (channel.network !== 'vk') {
    return { status: 400, code: 'validation_failed', detail: 'Suggest requires a VK channel' };
  }
  const existing = state.settings.find(
    (item) =>
      item.workspaceId === workspaceId &&
      item.projectId === projectId &&
      item.channelId === body.channelId,
  );
  if (existing) {
    return { status: 409, code: 'conflict', detail: 'Suggest settings already exist for channel' };
  }

  const now = nowIso();
  state.seq += 1;
  const config = withConfigDefaults(body.config ?? {});
  const created: SuggestSettings = {
    id: nextId('sugg', state.seq),
    workspaceId,
    projectId,
    channelId: body.channelId,
    status: 'draft',
    mode: body.mode ?? 'manual',
    config: config as unknown as SuggestSettings['config'],
    configVersion: 1,
    version: 1,
    createdAt: now,
    updatedAt: now,
  };
  state.settings.push(created);
  return created;
}

export function patchSuggestSettings(
  settingsId: string,
  body: SuggestSettingsPatch,
): SuggestSettings | SuggestMutationError {
  const current = findSettings(settingsId);
  if (!current) return { status: 404, code: 'not_found', detail: 'Settings not found' };
  if (body.version != null && body.version !== current.version) {
    return { status: 409, code: 'optimistic_lock_conflict', detail: 'Version conflict' };
  }
  const nextConfig =
    body.config != null
      ? (withConfigDefaults({
          ...withConfigDefaults(current.config),
          ...asRecord(body.config),
          filters: {
            ...withConfigDefaults(current.config).filters,
            ...asRecord(asRecord(body.config).filters),
          },
        }) as unknown as SuggestSettings['config'])
      : current.config;
  const updated: SuggestSettings = {
    ...current,
    mode: body.mode ?? current.mode,
    config: nextConfig,
    version: (current.version ?? 1) + 1,
    configVersion: (current.configVersion ?? 1) + (body.config ? 1 : 0),
    updatedAt: nowIso(),
  };
  state.settings = state.settings.map((item) => (item.id === settingsId ? updated : item));
  return updated;
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function setStatus(
  settingsId: string,
  status: SuggestSettings['status'],
): SuggestSettings | SuggestMutationError {
  const current = findSettings(settingsId);
  if (!current) return { status: 404, code: 'not_found', detail: 'Settings not found' };
  const updated: SuggestSettings = {
    ...current,
    status,
    version: (current.version ?? 1) + 1,
    updatedAt: nowIso(),
  };
  state.settings = state.settings.map((item) => (item.id === settingsId ? updated : item));
  return updated;
}

export function activateSuggestSettings(settingsId: string) {
  return setStatus(settingsId, 'active');
}

export function pauseSuggestSettings(settingsId: string) {
  return setStatus(settingsId, 'paused');
}

export function resumeSuggestSettings(settingsId: string) {
  return setStatus(settingsId, 'active');
}

export function copySuggestSettings(
  settingsId: string,
  body: SuggestSettingsCopyRequest,
): SuggestSettingsCopyResult | SuggestMutationError {
  const source = findSettings(settingsId);
  if (!source) return { status: 404, code: 'not_found', detail: 'Settings not found' };
  const results: NonNullable<SuggestSettingsCopyResult['results']> = [];
  for (const channelId of body.channelIds ?? []) {
    const channel = getChannel(channelId);
    if (!channel || channel.network !== 'vk') {
      results.push({ channelId, ok: false, error: 'not_vk', settingsId: null as never });
      continue;
    }
    const existing = state.settings.find(
      (item) =>
        item.workspaceId === source.workspaceId &&
        item.projectId === source.projectId &&
        item.channelId === channelId,
    );
    if (existing) {
      const patched = patchSuggestSettings(existing.id!, {
        mode: source.mode,
        config: source.config,
        version: existing.version,
      });
      if (isMutationError(patched)) {
        results.push({ channelId, ok: false, error: patched.detail });
      } else {
        results.push({ channelId, ok: true, settingsId: patched.id });
      }
      continue;
    }
    const created = createSuggestSettings(source.workspaceId!, source.projectId!, {
      channelId,
      mode: source.mode,
      config: source.config,
    });
    if (isMutationError(created)) {
      results.push({ channelId, ok: false, error: created.detail });
    } else {
      results.push({ channelId, ok: true, settingsId: created.id });
    }
  }
  return { results };
}

export function syncSuggestLast100(settingsId: string): SuggestSyncResult | SuggestMutationError {
  const settings = findSettings(settingsId);
  if (!settings) return { status: 404, code: 'not_found', detail: 'Settings not found' };
  const now = nowIso();
  state.seq += 1;
  const importedItem: SuggestItem = {
    id: nextId('sitem', state.seq),
    workspaceId: settings.workspaceId,
    channelId: settings.channelId,
    settingsId,
    vkPostId: `sync-${state.seq}`,
    authorVkId: '20001',
    text: `Импортировано синхронизацией #${state.seq}`,
    attachments: [],
    status: 'pending_moderation',
    reasons: [],
    createdAt: now,
    updatedAt: now,
  };
  state.items.unshift(importedItem);
  const updated: SuggestSettings = {
    ...settings,
    lastSyncAt: now,
    nextSyncAt: new Date(Date.now() + 15 * 60_000).toISOString(),
    updatedAt: now,
  };
  state.settings = state.settings.map((item) => (item.id === settingsId ? updated : item));
  return { imported: 1, removedExternally: 0, markedPublished: 0, status: 'ok' };
}

export function listSuggestItems(
  settingsId: string,
  filters?: {
    status?: string;
    authorVkId?: string;
    from?: string;
    to?: string;
  },
): SuggestItem[] {
  return state.items.filter((item) => {
    if (item.settingsId !== settingsId) return false;
    if (filters?.status && item.status !== filters.status) return false;
    if (filters?.authorVkId && item.authorVkId !== filters.authorVkId) return false;
    if (filters?.from && (item.createdAt ?? '') < filters.from) return false;
    if (filters?.to && (item.createdAt ?? '') > filters.to) return false;
    return true;
  });
}

export function seedSuggestItems(items: SuggestItem[]): void {
  state.items = [...state.items, ...items];
}

export function setSuggestSettingsWorkspace(settingsId: string, workspaceId: string): void {
  const settings = findSettings(settingsId);
  if (settings) settings.workspaceId = workspaceId;
}

export function countPending(settingsId: string): number {
  return state.items.filter(
    (item) => item.settingsId === settingsId && item.status === 'pending_moderation',
  ).length;
}

export function getSuggestItem(itemId: string): SuggestItem | undefined {
  return findItem(itemId);
}

export function approveSuggestItem(
  itemId: string,
  body?: SuggestItemApproveRequest,
): SuggestItem | SuggestMutationError {
  const item = findItem(itemId);
  if (!item) return { status: 404, code: 'not_found', detail: 'Item not found' };
  if (item.status !== 'pending_moderation' && item.status !== 'approved') {
    if (item.status === 'published' || item.status === 'scheduled') return item;
  }
  const now = nowIso();
  const updated: SuggestItem = {
    ...item,
    text: body?.message ?? item.text,
    attachments: body?.attachments ?? item.attachments,
    scheduledFor: body?.scheduledFor ?? item.scheduledFor,
    status: body?.scheduledFor ? 'scheduled' : 'published',
    decidedAt: now,
    decidedBy: DEMO_USER_ID,
    publishedRemotePostId: body?.scheduledFor ? undefined : `wall-pub-${item.vkPostId}`,
    updatedAt: now,
    reasons: [],
  };
  state.items = state.items.map((entry) => (entry.id === itemId ? updated : entry));
  return updated;
}

export function rejectSuggestItem(
  itemId: string,
  body?: SuggestItemRejectRequest,
): SuggestItem | SuggestMutationError {
  const item = findItem(itemId);
  if (!item) return { status: 404, code: 'not_found', detail: 'Item not found' };
  const now = nowIso();
  const updated: SuggestItem = {
    ...item,
    status: body?.deleteRemote === false ? 'rejected' : 'deleted_remote',
    decidedAt: now,
    decidedBy: DEMO_USER_ID,
    updatedAt: now,
  };
  state.items = state.items.map((entry) => (entry.id === itemId ? updated : entry));
  return updated;
}

export function bulkSuggestItems(
  settingsId: string,
  body: SuggestBulkRequest,
): SuggestBulkJob | SuggestMutationError {
  const settings = findSettings(settingsId);
  if (!settings) return { status: 404, code: 'not_found', detail: 'Settings not found' };

  let targets: SuggestItem[] = [];
  if (body.action === 'approve_all' || body.action === 'reject_all') {
    targets = listSuggestItems(settingsId, { status: 'pending_moderation' });
  } else {
    const ids = new Set(body.ids ?? []);
    targets = state.items.filter(
      (item) => item.settingsId === settingsId && item.id && ids.has(item.id),
    );
  }

  const now = nowIso();
  state.seq += 1;
  let succeeded = 0;
  let failed = 0;
  for (const item of targets) {
    const result =
      body.action === 'approve_all' || body.action === 'approve_ids'
        ? approveSuggestItem(item.id!)
        : rejectSuggestItem(item.id!, { deleteRemote: body.deleteRemote ?? true });
    if (isMutationError(result)) failed += 1;
    else succeeded += 1;
  }

  const job: SuggestBulkJob = {
    id: nextId('sjob', state.seq),
    workspaceId: settings.workspaceId,
    settingsId,
    channelId: settings.channelId,
    action: body.action,
    status: 'succeeded',
    total: targets.length,
    processed: targets.length,
    succeeded,
    failed,
    progressPercent: 100,
    createdAt: now,
    updatedAt: now,
    finishedAt: now,
  };
  state.jobs.unshift(job);
  return job;
}

export function getSuggestBulkJob(jobId: string): SuggestBulkJob | undefined {
  return state.jobs.find((job) => job.id === jobId);
}

function roleFromAuth(request: Request): WorkspaceRole {
  const auth = request.headers.get('Authorization') ?? '';
  const token = auth.replace(/^Bearer\s+/i, '');
  if (token.includes('viewer')) return 'viewer';
  if (token.includes('author')) return 'author';
  if (token.includes('editor')) return 'editor';
  if (token.includes('admin')) return 'admin';
  return 'owner';
}

function problem(status: number, code: string, detail: string, title?: string) {
  const body: ProblemDetail = {
    type: `https://posts.ru/problems/${code}`,
    title: title ?? code,
    status,
    detail,
    code,
    instance: '/api/v1/suggest',
  };
  return HttpResponse.json(body, {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

function requireBearer(request: Request) {
  const auth = request.headers.get('Authorization');
  if (!auth?.startsWith('Bearer ')) {
    return problem(401, 'unauthorized', 'Missing bearer token');
  }
  return null;
}

function requirePermission(request: Request, action: Parameters<typeof can>[1]) {
  const unauthorized = requireBearer(request);
  if (unauthorized) return unauthorized;
  const role = roleFromAuth(request);
  if (!can(role, action)) {
    return problem(403, INSUFFICIENT_PERMISSIONS_CODE, 'Insufficient permissions');
  }
  return null;
}

function mutationResponse(
  result:
    | SuggestSettings
    | SuggestItem
    | SuggestBulkJob
    | SuggestSyncResult
    | SuggestSettingsCopyResult
    | SuggestMutationError,
) {
  if (isMutationError(result)) {
    return problem(result.status, result.code, result.detail, result.code);
  }
  return HttpResponse.json(result);
}

/** Full Suggest handlers — spread into handlers.ts as `...suggestHandlers`. */
export const suggestHandlers: HttpHandler[] = [
  http.get(
    '/api/v1/workspaces/:workspaceId/projects/:projectId/suggest/settings',
    ({ request, params }) => {
      const denied = requirePermission(request, 'automations.view');
      if (denied) return denied;
      const items = listSuggestSettings({
        workspaceId: String(params.workspaceId),
        projectId: String(params.projectId),
      });
      return HttpResponse.json({ items, hasMore: false });
    },
  ),

  http.post(
    '/api/v1/workspaces/:workspaceId/projects/:projectId/suggest/settings',
    async ({ request, params }) => {
      const denied = requirePermission(request, 'automations.manage');
      if (denied) return denied;
      const body = (await request.json()) as SuggestSettingsCreate;
      const result = createSuggestSettings(
        String(params.workspaceId),
        String(params.projectId),
        body,
      );
      if (isMutationError(result)) return mutationResponse(result);
      return HttpResponse.json(result, { status: 201 });
    },
  ),

  http.get('/api/v1/suggest/settings/:settingsId', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.view');
    if (denied) return denied;
    const settings = getSuggestSettings(String(params.settingsId));
    if (!settings) return problem(404, 'not_found', 'Settings not found');
    return HttpResponse.json(settings);
  }),

  http.patch('/api/v1/suggest/settings/:settingsId', async ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    const body = (await request.json()) as SuggestSettingsPatch;
    return mutationResponse(patchSuggestSettings(String(params.settingsId), body));
  }),

  http.post('/api/v1/suggest/settings/:settingsId/activate', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    return mutationResponse(activateSuggestSettings(String(params.settingsId)));
  }),

  http.post('/api/v1/suggest/settings/:settingsId/pause', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    return mutationResponse(pauseSuggestSettings(String(params.settingsId)));
  }),

  http.post('/api/v1/suggest/settings/:settingsId/resume', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    return mutationResponse(resumeSuggestSettings(String(params.settingsId)));
  }),

  http.post('/api/v1/suggest/settings/:settingsId/copy', async ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    const body = (await request.json()) as SuggestSettingsCopyRequest;
    return mutationResponse(copySuggestSettings(String(params.settingsId), body));
  }),

  http.post('/api/v1/suggest/settings/:settingsId/sync-last-100', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    return mutationResponse(syncSuggestLast100(String(params.settingsId)));
  }),

  http.get('/api/v1/suggest/settings/:settingsId/items', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.view');
    if (denied) return denied;
    if (!getSuggestSettings(String(params.settingsId))) {
      return problem(404, 'not_found', 'Settings not found');
    }
    const url = new URL(request.url);
    const items = listSuggestItems(String(params.settingsId), {
      status: url.searchParams.get('status') ?? undefined,
      authorVkId: url.searchParams.get('authorVkId') ?? undefined,
      from: url.searchParams.get('from') ?? undefined,
      to: url.searchParams.get('to') ?? undefined,
    });
    const limitRaw = Number(url.searchParams.get('limit') ?? '50');
    const limit = Number.isFinite(limitRaw) ? Math.min(100, Math.max(1, limitRaw)) : 50;
    const cursor = url.searchParams.get('cursor');
    let start = 0;
    if (cursor) {
      const index = items.findIndex((item) => item.id === cursor);
      start = index >= 0 ? index + 1 : items.length;
    }
    const page = items.slice(start, start + limit);
    const hasMore = start + limit < items.length;
    return HttpResponse.json({
      items: page,
      hasMore,
      nextCursor: hasMore ? page[page.length - 1]?.id : undefined,
    });
  }),

  http.post('/api/v1/suggest/settings/:settingsId/items/bulk', async ({ request, params }) => {
    const denied = requirePermission(request, 'automations.moderate');
    if (denied) return denied;
    const body = (await request.json()) as SuggestBulkRequest;
    return mutationResponse(bulkSuggestItems(String(params.settingsId), body));
  }),

  http.get('/api/v1/suggest/bulk-jobs/:jobId', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.view');
    if (denied) return denied;
    const job = getSuggestBulkJob(String(params.jobId));
    if (!job) return problem(404, 'not_found', 'Bulk job not found');
    return HttpResponse.json(job);
  }),

  http.get('/api/v1/suggest/items/:itemId', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.view');
    if (denied) return denied;
    const item = getSuggestItem(String(params.itemId));
    if (!item) return problem(404, 'not_found', 'Item not found');
    return HttpResponse.json(item);
  }),

  http.post('/api/v1/suggest/items/:itemId/approve', async ({ request, params }) => {
    const denied = requirePermission(request, 'automations.moderate');
    if (denied) return denied;
    let body: SuggestItemApproveRequest = {};
    try {
      body = (await request.json()) as SuggestItemApproveRequest;
    } catch {
      body = {};
    }
    return mutationResponse(approveSuggestItem(String(params.itemId), body));
  }),

  http.post('/api/v1/suggest/items/:itemId/reject', async ({ request, params }) => {
    const denied = requirePermission(request, 'automations.moderate');
    if (denied) return denied;
    let body: SuggestItemRejectRequest = {};
    try {
      body = (await request.json()) as SuggestItemRejectRequest;
    } catch {
      body = {};
    }
    return mutationResponse(rejectSuggestItem(String(params.itemId), body));
  }),
];
