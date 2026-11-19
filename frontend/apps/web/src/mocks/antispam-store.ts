/**
 * FE-23 / BE-35 MSW store for Antispam rules, word lists, log, restore, scan, stats.
 */
import type {
  AntispamLogEntry,
  AntispamRestoreRequest,
  AntispamRule,
  AntispamRuleCreate,
  AntispamRulePatch,
  AntispamRuleStats,
  AntispamScanResponse,
  AntispamWordList,
  AntispamWordListCreate,
  AntispamWordListPatch,
  EventSubscriptionResponse,
  ProblemDetail,
} from '@posts/api-client';
import { http, HttpResponse, type HttpHandler } from 'msw';

import { can, type WorkspaceRole } from '@/shared/lib/permissions';
import { INSUFFICIENT_PERMISSIONS_CODE } from '@/shared/lib/errors';

import { CHANNEL_IDS, getChannel } from './channel-store';
import { DEFAULT_PROJECT_ID, PERSONAL_WORKSPACE_ID } from './workspace-store';

export const RULE_IDS = {
  vkActive: '019a0000-asrule-7000-8000-000000000001',
  tgActive: '019a0000-asrule-7000-8000-000000000002',
} as const;

export const LIST_IDS = {
  stopWords: '019a0000-aslist-7000-8000-000000000001',
} as const;

export const LOG_IDS = {
  vkDeleted: '019a0000-aslog-7000-8000-000000000001',
  tgDeleted: '019a0000-aslog-7000-8000-000000000002',
} as const;

export type AntispamDemoSeed = {
  workspaceId: string;
  channelIds: string[];
  eventConsumers: EventSubscriptionResponse['consumers'];
};

type AntispamMutationError = {
  status: number;
  code: string;
  detail: string;
};

type StoreState = {
  seed: AntispamDemoSeed;
  rules: AntispamRule[];
  wordLists: AntispamWordList[];
  logs: AntispamLogEntry[];
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

function defaultConfig(overrides?: Record<string, unknown>): Record<string, unknown> {
  return {
    filters: {
      keywords: { black: [], white: [], black_list_ids: [], white_list_ids: [] },
      links: { all: false, vk: false, domain_whitelist: [] },
      attachments: {
        photo: false,
        photo_without_text: false,
        video: false,
        audio: false,
        doc: false,
        gif: false,
        sticker: false,
      },
      caps: { enabled: false, ratio: 0.7, min_letters: 5 },
      repeat: { enabled: false, window_hours: 12 },
      authors: { communities: false, non_members: false, sender_chat: false },
      context: { under_ads: false, under_pinned: false },
    },
    actions: {
      delete: true,
      ban: { enabled: false, days: 7, reason: '', comment_visible: false },
      reply: {
        enabled: false,
        template: 'Ваше сообщение удалено: {reasons}',
      },
    },
    exceptions: {
      admin_ids: [],
      user_whitelist: [],
      exclude_own_community: true,
    },
    ...overrides,
  };
}

function mergeConfig(
  base: Record<string, unknown>,
  patch?: Record<string, unknown> | null,
): Record<string, unknown> {
  if (!patch) return base;
  return { ...base, ...patch };
}

function createInitialState(): StoreState {
  const now = nowIso();
  const stopList: AntispamWordList = {
    id: LIST_IDS.stopWords,
    workspaceId: PERSONAL_WORKSPACE_ID,
    name: 'Стоп-слова',
    words: ['спам', 'реклама'],
    mode: 'black',
    version: 1,
    updatedAt: now,
  };

  const vkRule: AntispamRule = {
    id: RULE_IDS.vkActive,
    workspaceId: PERSONAL_WORKSPACE_ID,
    projectId: DEFAULT_PROJECT_ID,
    channelId: CHANNEL_IDS.vkNews,
    name: 'VK комментарии',
    status: 'active',
    priority: 100,
    sections: ['wall_comment'],
    config: defaultConfig({
      filters: {
        ...(defaultConfig().filters as object),
        keywords: {
          black: ['спам'],
          white: [],
          black_list_ids: [LIST_IDS.stopWords],
          white_list_ids: [],
        },
      },
    }) as never,
    configVersion: 1,
    dryRun: false,
    version: 1,
    createdAt: now,
    updatedAt: now,
  };

  const tgRule: AntispamRule = {
    id: RULE_IDS.tgActive,
    workspaceId: PERSONAL_WORKSPACE_ID,
    projectId: DEFAULT_PROJECT_ID,
    channelId: CHANNEL_IDS.tgNews,
    name: 'TG обсуждения',
    status: 'active',
    priority: 110,
    sections: ['tg_discussion'],
    config: defaultConfig({
      filters: {
        ...(defaultConfig().filters as object),
        keywords: {
          black: ['спам'],
          white: [],
          black_list_ids: [],
          white_list_ids: [],
        },
        authors: { communities: false, non_members: false, sender_chat: true },
      },
    }) as never,
    configVersion: 1,
    dryRun: false,
    version: 1,
    createdAt: now,
    updatedAt: now,
  };

  const vkLog: AntispamLogEntry = {
    id: LOG_IDS.vkDeleted,
    workspaceId: PERSONAL_WORKSPACE_ID,
    channelId: CHANNEL_IDS.vkNews,
    ruleId: RULE_IDS.vkActive,
    section: 'wall_comment',
    remoteRef: { commentId: '12345' } as never,
    authorRef: 'vk:1001',
    textSnapshot: 'Это спам предложение!',
    attachmentsSummary: '',
    reasons: ['keyword_black'],
    action: 'deleted',
    state: 'done',
    restorable: true,
    remoteActionIds: ['019a0000-ra-7000-8000-000000000001'],
    createdAt: minutesAgo(45),
  };

  const tgLog: AntispamLogEntry = {
    id: LOG_IDS.tgDeleted,
    workspaceId: PERSONAL_WORKSPACE_ID,
    channelId: CHANNEL_IDS.tgNews,
    ruleId: RULE_IDS.tgActive,
    section: 'tg_discussion',
    remoteRef: { messageId: '42' } as never,
    authorRef: 'tg:2002',
    textSnapshot: 'Купите спам сейчас',
    attachmentsSummary: '',
    reasons: ['keyword_black'],
    action: 'deleted',
    state: 'done',
    restorable: false,
    remoteActionIds: [],
    createdAt: minutesAgo(20),
  };

  return {
    seq: 100,
    seed: {
      workspaceId: PERSONAL_WORKSPACE_ID,
      channelIds: [CHANNEL_IDS.vkNews, CHANNEL_IDS.tgNews, CHANNEL_IDS.maxBrand],
      eventConsumers: ['antispam'],
    },
    rules: [vkRule, tgRule],
    wordLists: [stopList],
    logs: [vkLog, tgLog],
  };
}

let state = createInitialState();

export function resetAntispamStore(): void {
  state = createInitialState();
}

export function getAntispamDemoSeed(): AntispamDemoSeed {
  return state.seed;
}

export function listAntispamRules(params?: {
  workspaceId?: string;
  projectId?: string;
}): AntispamRule[] {
  return state.rules
    .filter((rule) => {
      if (params?.workspaceId && rule.workspaceId !== params.workspaceId) return false;
      if (params?.projectId && rule.projectId !== params.projectId) return false;
      return rule.status !== 'archived';
    })
    .sort((a, b) => (a.priority ?? 0) - (b.priority ?? 0));
}

export function getAntispamRule(ruleId: string): AntispamRule | undefined {
  return state.rules.find((rule) => rule.id === ruleId);
}

export function createAntispamRule(
  workspaceId: string,
  projectId: string,
  body: AntispamRuleCreate,
): AntispamRule | AntispamMutationError {
  if (!body.name?.trim() || !body.channelId) {
    return { status: 400, code: 'validation_failed', detail: 'name and channelId required' };
  }
  const channel = getChannel(body.channelId);
  if (!channel || channel.workspaceId !== workspaceId) {
    return { status: 404, code: 'not_found', detail: 'Channel not found' };
  }
  if (!['vk', 'tg', 'max'].includes(channel.network ?? '')) {
    return { status: 400, code: 'validation_failed', detail: 'Unsupported network' };
  }

  state.seq += 1;
  const now = nowIso();
  const rule: AntispamRule = {
    id: nextId('asrule', state.seq),
    workspaceId,
    projectId,
    channelId: body.channelId,
    name: body.name.trim(),
    status: 'draft',
    priority: body.priority ?? 100,
    sections: body.sections ?? [],
    config: mergeConfig(defaultConfig(), body.config as Record<string, unknown> | undefined) as never,
    configVersion: 1,
    dryRun: Boolean(body.dryRun),
    version: 1,
    createdAt: now,
    updatedAt: now,
  };
  state.rules.unshift(rule);

  // Seed a matching history hit when black words present (helps e2e).
  const cfg = (rule.config ?? {}) as Record<string, unknown>;
  const filters = (cfg.filters ?? {}) as Record<string, unknown>;
  const keywords = (filters.keywords ?? {}) as Record<string, unknown>;
  const black = Array.isArray(keywords.black) ? keywords.black.map(String) : [];
  if (black.length > 0 && channel.network === 'vk') {
    state.seq += 1;
    state.logs.unshift({
      id: nextId('aslog', state.seq),
      workspaceId,
      channelId: body.channelId,
      ruleId: rule.id,
      section: rule.sections?.[0] ?? 'wall_comment',
      remoteRef: { commentId: String(9000 + state.seq) } as never,
      authorRef: 'vk:demo',
      textSnapshot: `Комментарий со словом «${black[0]}»`,
      attachmentsSummary: '',
      reasons: ['keyword_black'],
      action: 'deleted',
      state: 'done',
      restorable: true,
      remoteActionIds: [],
      createdAt: now,
    });
  }

  return rule;
}

export function patchAntispamRule(
  ruleId: string,
  body: AntispamRulePatch,
): AntispamRule | AntispamMutationError {
  const rule = getAntispamRule(ruleId);
  if (!rule) return { status: 404, code: 'not_found', detail: 'Rule not found' };
  if (body.version != null && body.version !== rule.version) {
    return { status: 409, code: 'optimistic_lock_conflict', detail: 'Rule version mismatch' };
  }

  const updated: AntispamRule = {
    ...rule,
    name: body.name?.trim() ?? rule.name,
    priority: body.priority ?? rule.priority,
    sections: body.sections ?? rule.sections,
    dryRun: body.dryRun ?? rule.dryRun,
    config: (body.config
      ? mergeConfig(
          { ...(rule.config as Record<string, unknown>) },
          body.config as Record<string, unknown>,
        )
      : rule.config) as never,
    configVersion: body.config ? (rule.configVersion ?? 1) + 1 : rule.configVersion,
    version: (rule.version ?? 1) + 1,
    updatedAt: nowIso(),
  };
  state.rules = state.rules.map((item) => (item.id === ruleId ? updated : item));
  return updated;
}

export function activateAntispamRule(ruleId: string): AntispamRule | AntispamMutationError {
  const rule = getAntispamRule(ruleId);
  if (!rule) return { status: 404, code: 'not_found', detail: 'Rule not found' };
  const updated: AntispamRule = { ...rule, status: 'active', updatedAt: nowIso() };
  state.rules = state.rules.map((item) => (item.id === ruleId ? updated : item));
  return updated;
}

export function pauseAntispamRule(ruleId: string): AntispamRule | AntispamMutationError {
  const rule = getAntispamRule(ruleId);
  if (!rule) return { status: 404, code: 'not_found', detail: 'Rule not found' };
  const updated: AntispamRule = { ...rule, status: 'paused', updatedAt: nowIso() };
  state.rules = state.rules.map((item) => (item.id === ruleId ? updated : item));
  return updated;
}

export function resumeAntispamRule(ruleId: string): AntispamRule | AntispamMutationError {
  const rule = getAntispamRule(ruleId);
  if (!rule) return { status: 404, code: 'not_found', detail: 'Rule not found' };
  if (rule.status !== 'paused') {
    return { status: 409, code: 'conflict', detail: 'Rule is not paused' };
  }
  return activateAntispamRule(ruleId);
}

export function archiveAntispamRule(ruleId: string): AntispamRule | AntispamMutationError {
  const rule = getAntispamRule(ruleId);
  if (!rule) return { status: 404, code: 'not_found', detail: 'Rule not found' };
  const updated: AntispamRule = { ...rule, status: 'archived', updatedAt: nowIso() };
  state.rules = state.rules.map((item) => (item.id === ruleId ? updated : item));
  return updated;
}

export function getAntispamRuleStats(ruleId: string): AntispamRuleStats | AntispamMutationError {
  const rule = getAntispamRule(ruleId);
  if (!rule) return { status: 404, code: 'not_found', detail: 'Rule not found' };
  const now = Date.now();
  const day = 24 * 60 * 60_000;
  const logs = state.logs.filter((entry) => entry.ruleId === ruleId);
  const byReason: Record<string, number> = {};
  let hits24h = 0;
  let hits7d = 0;
  for (const entry of logs) {
    const created = entry.createdAt ? new Date(entry.createdAt).getTime() : 0;
    if (now - created <= day) hits24h += 1;
    if (now - created <= 7 * day) hits7d += 1;
    for (const reason of entry.reasons ?? []) {
      byReason[reason] = (byReason[reason] ?? 0) + 1;
    }
  }
  return {
    ruleId,
    hits: logs.length,
    hits24h,
    hits7d,
    byReason,
  };
}

export function scanAntispamRule(ruleId: string): AntispamScanResponse | AntispamMutationError {
  const rule = getAntispamRule(ruleId);
  if (!rule) return { status: 404, code: 'not_found', detail: 'Rule not found' };
  const channel = getChannel(rule.channelId ?? '');
  if (!channel) return { status: 404, code: 'not_found', detail: 'Channel not found' };
  if (channel.network === 'tg' || channel.network === 'max') {
    return {
      status: 422,
      code: 'antispam_tg_scan_unsupported',
      detail: 'Manual scan is only supported for VK',
    };
  }
  state.seq += 1;
  return {
    jobId: nextId('asjob', state.seq),
    status: 'queued',
    message: 'Сканирование последних 100 комментариев стены запущено',
  };
}

export function listAntispamLog(params: {
  workspaceId: string;
  channelId?: string;
  section?: string;
  ruleId?: string;
  action?: string;
  cursor?: string;
  limit?: number;
}): { items: AntispamLogEntry[]; nextCursor?: string; hasMore: boolean } {
  const limit = params.limit ?? 50;
  const filtered = state.logs
    .filter((entry) => {
      if (entry.workspaceId !== params.workspaceId) return false;
      if (params.channelId && entry.channelId !== params.channelId) return false;
      if (params.section && entry.section !== params.section) return false;
      if (params.ruleId && entry.ruleId !== params.ruleId) return false;
      if (params.action && entry.action !== params.action) return false;
      return true;
    })
    .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));

  let start = 0;
  if (params.cursor) {
    const idx = filtered.findIndex((entry) => entry.id === params.cursor);
    start = idx >= 0 ? idx + 1 : 0;
  }
  const slice = filtered.slice(start, start + limit);
  const last = slice[slice.length - 1];
  const hasMore = start + slice.length < filtered.length;
  return {
    items: slice,
    nextCursor: hasMore && last?.id ? last.id : undefined,
    hasMore,
  };
}

export function restoreAntispamLog(
  logId: string,
  body: AntispamRestoreRequest = {},
): AntispamLogEntry | AntispamMutationError {
  const entry = state.logs.find((item) => item.id === logId);
  if (!entry) return { status: 404, code: 'not_found', detail: 'Log entry not found' };
  if (!entry.restorable) {
    return {
      status: 422,
      code: 'antispam_restore_unsupported',
      detail: 'Restore is only available for VK comments',
    };
  }
  if (entry.state === 'restored') {
    return { status: 409, code: 'conflict', detail: 'Already restored' };
  }
  const updated: AntispamLogEntry = {
    ...entry,
    state: 'restored',
    restoredAt: nowIso(),
    restoredBy: '019a0000-user-7000-8000-000000000001',
    action: body.unban ? entry.action : entry.action,
  };
  state.logs = state.logs.map((item) => (item.id === logId ? updated : item));
  return updated;
}

export function listAntispamWordLists(workspaceId: string): AntispamWordList[] {
  return state.wordLists.filter((list) => list.workspaceId === workspaceId);
}

export function createAntispamWordList(
  workspaceId: string,
  body: AntispamWordListCreate,
): AntispamWordList | AntispamMutationError {
  if (!body.name?.trim() || !body.mode) {
    return { status: 400, code: 'validation_failed', detail: 'name and mode required' };
  }
  if (body.mode !== 'black' && body.mode !== 'white') {
    return { status: 400, code: 'validation_failed', detail: 'mode must be black or white' };
  }
  state.seq += 1;
  const list: AntispamWordList = {
    id: nextId('aslist', state.seq),
    workspaceId,
    name: body.name.trim(),
    words: body.words ?? [],
    mode: body.mode,
    version: 1,
    updatedAt: nowIso(),
  };
  state.wordLists.unshift(list);
  return list;
}

export function patchAntispamWordList(
  listId: string,
  body: AntispamWordListPatch,
): AntispamWordList | AntispamMutationError {
  const list = state.wordLists.find((item) => item.id === listId);
  if (!list) return { status: 404, code: 'not_found', detail: 'Word list not found' };
  if (body.version != null && body.version !== list.version) {
    return { status: 409, code: 'optimistic_lock_conflict', detail: 'Word list version mismatch' };
  }
  const updated: AntispamWordList = {
    ...list,
    name: body.name?.trim() ?? list.name,
    words: body.words ?? list.words,
    mode: body.mode ?? list.mode,
    version: (list.version ?? 1) + 1,
    updatedAt: nowIso(),
  };
  state.wordLists = state.wordLists.map((item) => (item.id === listId ? updated : item));
  return updated;
}

export function deleteAntispamWordList(listId: string): true | AntispamMutationError {
  const exists = state.wordLists.some((item) => item.id === listId);
  if (!exists) return { status: 404, code: 'not_found', detail: 'Word list not found' };
  state.wordLists = state.wordLists.filter((item) => item.id !== listId);
  return true;
}

function isMutationError(value: unknown): value is AntispamMutationError {
  return Boolean(value && typeof value === 'object' && 'code' in value && 'status' in value);
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
    instance: '/api/v1/antispam',
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
    | AntispamRule
    | AntispamLogEntry
    | AntispamWordList
    | AntispamRuleStats
    | AntispamScanResponse
    | AntispamMutationError,
) {
  if (isMutationError(result)) {
    return problem(result.status, result.code, result.detail, result.code);
  }
  return HttpResponse.json(result);
}

/** Full Antispam handlers — spread into handlers.ts. */
export const antispamHandlers: HttpHandler[] = [
  http.get(
    '/api/v1/workspaces/:workspaceId/projects/:projectId/antispam/rules',
    ({ request, params }) => {
      const denied = requirePermission(request, 'automations.view');
      if (denied) return denied;
      const items = listAntispamRules({
        workspaceId: String(params.workspaceId),
        projectId: String(params.projectId),
      });
      return HttpResponse.json({ items, hasMore: false });
    },
  ),

  http.post(
    '/api/v1/workspaces/:workspaceId/projects/:projectId/antispam/rules',
    async ({ request, params }) => {
      const denied = requirePermission(request, 'automations.manage');
      if (denied) return denied;
      const body = (await request.json()) as AntispamRuleCreate;
      const result = createAntispamRule(
        String(params.workspaceId),
        String(params.projectId),
        body,
      );
      if (isMutationError(result)) return mutationResponse(result);
      return HttpResponse.json(result, { status: 201 });
    },
  ),

  http.get('/api/v1/antispam/rules/:ruleId', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.view');
    if (denied) return denied;
    const rule = getAntispamRule(String(params.ruleId));
    if (!rule) return problem(404, 'not_found', 'Rule not found');
    return HttpResponse.json(rule);
  }),

  http.patch('/api/v1/antispam/rules/:ruleId', async ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    const body = (await request.json()) as AntispamRulePatch;
    return mutationResponse(patchAntispamRule(String(params.ruleId), body));
  }),

  http.post('/api/v1/antispam/rules/:ruleId/activate', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    return mutationResponse(activateAntispamRule(String(params.ruleId)));
  }),

  http.post('/api/v1/antispam/rules/:ruleId/pause', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    return mutationResponse(pauseAntispamRule(String(params.ruleId)));
  }),

  http.post('/api/v1/antispam/rules/:ruleId/resume', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    return mutationResponse(resumeAntispamRule(String(params.ruleId)));
  }),

  http.post('/api/v1/antispam/rules/:ruleId/archive', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    return mutationResponse(archiveAntispamRule(String(params.ruleId)));
  }),

  http.get('/api/v1/antispam/rules/:ruleId/stats', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.view');
    if (denied) return denied;
    return mutationResponse(getAntispamRuleStats(String(params.ruleId)));
  }),

  http.post('/api/v1/antispam/rules/:ruleId/scan', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.moderate');
    if (denied) return denied;
    return mutationResponse(scanAntispamRule(String(params.ruleId)));
  }),

  http.get('/api/v1/workspaces/:workspaceId/antispam/log', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.view');
    if (denied) return denied;
    const url = new URL(request.url);
    return HttpResponse.json(
      listAntispamLog({
        workspaceId: String(params.workspaceId),
        channelId: url.searchParams.get('channelId') ?? undefined,
        section: url.searchParams.get('section') ?? undefined,
        ruleId: url.searchParams.get('ruleId') ?? undefined,
        action: url.searchParams.get('action') ?? undefined,
        cursor: url.searchParams.get('cursor') ?? undefined,
        limit: url.searchParams.get('limit')
          ? Number(url.searchParams.get('limit'))
          : 50,
      }),
    );
  }),

  http.post('/api/v1/antispam/log/:logId/restore', async ({ request, params }) => {
    const denied = requirePermission(request, 'automations.moderate');
    if (denied) return denied;
    const body = (await request.json().catch(() => ({}))) as AntispamRestoreRequest;
    return mutationResponse(restoreAntispamLog(String(params.logId), body));
  }),

  http.get('/api/v1/workspaces/:workspaceId/antispam/word-lists', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.view');
    if (denied) return denied;
    const items = listAntispamWordLists(String(params.workspaceId));
    return HttpResponse.json({ items, hasMore: false });
  }),

  http.post(
    '/api/v1/workspaces/:workspaceId/antispam/word-lists',
    async ({ request, params }) => {
      const denied = requirePermission(request, 'automations.manage');
      if (denied) return denied;
      const body = (await request.json()) as AntispamWordListCreate;
      const result = createAntispamWordList(String(params.workspaceId), body);
      if (isMutationError(result)) return mutationResponse(result);
      return HttpResponse.json(result, { status: 201 });
    },
  ),

  http.patch('/api/v1/antispam/word-lists/:listId', async ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    const body = (await request.json()) as AntispamWordListPatch;
    return mutationResponse(patchAntispamWordList(String(params.listId), body));
  }),

  http.delete('/api/v1/antispam/word-lists/:listId', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    const result = deleteAntispamWordList(String(params.listId));
    if (isMutationError(result)) return mutationResponse(result);
    return new HttpResponse(null, { status: 204 });
  }),
];
