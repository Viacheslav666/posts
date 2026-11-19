/**
 * FE-24 / BE-36 MSW store for Crosspost rules, journal, preview, retry.
 */
import type {
  CrosspostItem,
  CrosspostPreviewRequest,
  CrosspostPreviewResponse,
  CrosspostRule,
  CrosspostRuleCreate,
  CrosspostRulePatch,
  CrosspostTarget,
  CrosspostWarning,
  EventSubscriptionResponse,
  ProblemDetail,
} from '@posts/api-client';
import { http, HttpResponse, type HttpHandler } from 'msw';

import { can, type WorkspaceRole } from '@/shared/lib/permissions';
import { INSUFFICIENT_PERMISSIONS_CODE, PLAN_LIMIT_EXCEEDED_CODE } from '@/shared/lib/errors';

import { CHANNEL_IDS, getChannel } from './channel-store';
import { DEFAULT_PROJECT_ID, PERSONAL_WORKSPACE_ID } from './workspace-store';

export const RULE_IDS = {
  vkToTgMax: '019a0000-xpost-7000-8000-000000000001',
} as const;

export const ITEM_IDS = {
  published1: '019a0000-xpitem-7000-8000-000000000001',
  failed1: '019a0000-xpitem-7000-8000-000000000002',
  filtered1: '019a0000-xpitem-7000-8000-000000000003',
} as const;

const CROSSPOST_RULE_LIMIT = 20;

export type CrosspostDemoSeed = {
  workspaceId: string;
  sourceChannelIds: string[];
  targetChannelIds: string[];
  eventConsumers: EventSubscriptionResponse['consumers'];
};

type CrosspostMutationError = {
  status: number;
  code: string;
  detail: string;
};

type StoreState = {
  seed: CrosspostDemoSeed;
  rules: CrosspostRule[];
  items: CrosspostItem[];
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
      exclude_links: false,
      exclude_snippets: true,
      exclude_articles: true,
      skip_if_no_text: false,
      skip_if_no_images: false,
      exclude_video: false,
      exclude_clips: true,
      exclude_poll: true,
      exclude_geo: true,
      exclude_audio: true,
      exclude_ads: true,
      exclude_reposts: false,
      include_reposts: true,
      only_group_posts_in_tg_groups: true,
      keyword_include: [],
      keyword_exclude: [],
    },
    transform: {
      remove_links: false,
      remove_hashtags: false,
      add_source_link: false,
      preserve_formatting: true,
      close_comments: false,
      publish_as_vk_repost: false,
      long_text_mode: 'split',
      vk_video_as_link: true,
    },
    delay_minutes: 0,
    include_posts_published_via_posts: false,
    include_suggest_published: false,
    schedule: {
      enabled: false,
      timezone: 'Europe/Moscow',
      days_of_week: [1, 2, 3, 4, 5, 6, 7],
      hours_from: 0,
      hours_to: 24,
    },
    ...overrides,
  };
}

function makeTarget(channelId: string, network: string): CrosspostTarget {
  return {
    id: nextId('xptgt', Math.floor(Math.random() * 1_000_000)),
    targetChannelId: channelId,
    network,
    options: {},
  };
}

function detectCycleWarnings(
  rules: CrosspostRule[],
  candidate?: { sourceChannelId: string; targetChannelIds: string[] },
): CrosspostWarning[] {
  const adj = new Map<string, string[]>();
  const addEdge = (from: string, to: string) => {
    if (!from || !to || from === to) return;
    const list = adj.get(from) ?? [];
    list.push(to);
    adj.set(from, list);
  };

  for (const rule of rules) {
    if (rule.status === 'draft') continue;
    const source = rule.sourceChannelId;
    if (!source) continue;
    for (const target of rule.targets ?? []) {
      if (target.targetChannelId) addEdge(source, target.targetChannelId);
    }
  }
  if (candidate) {
    for (const targetId of candidate.targetChannelIds) {
      addEdge(candidate.sourceChannelId, targetId);
    }
  }

  const warnings: CrosspostWarning[] = [];
  const visited = new Set<string>();

  const dfs = (node: string, stack: string[], onStack: Set<string>) => {
    stack.push(node);
    onStack.add(node);
    visited.add(node);
    for (const next of adj.get(node) ?? []) {
      if (onStack.has(next)) {
        const idx = stack.indexOf(next);
        const path = [...stack.slice(idx), next];
        warnings.push({
          code: 'cycle_detected',
          message:
            'Правила образуют цикл; повторов не будет, Posts защищает от петель',
          channelPath: path,
        });
        continue;
      }
      if (!visited.has(next)) {
        dfs(next, stack, onStack);
      }
    }
    stack.pop();
    onStack.delete(node);
  };

  for (const start of adj.keys()) {
    if (!visited.has(start)) {
      dfs(start, [], new Set());
    }
  }
  return warnings;
}

function createInitialState(): StoreState {
  const now = nowIso();
  const rule: CrosspostRule = {
    id: RULE_IDS.vkToTgMax,
    workspaceId: PERSONAL_WORKSPACE_ID,
    projectId: DEFAULT_PROJECT_ID,
    sourceChannelId: CHANNEL_IDS.vkNews,
    sourceNetwork: 'vk',
    name: 'VK → TG + Max',
    status: 'active',
    config: defaultConfig({ delay_minutes: 5 }) as never,
    configVersion: 1,
    version: 1,
    targets: [
      makeTarget(CHANNEL_IDS.tgNews, 'tg'),
      makeTarget(CHANNEL_IDS.maxBrand, 'max'),
    ],
    warnings: [],
    createdAt: now,
    updatedAt: now,
  };

  return {
    seq: 100,
    seed: {
      workspaceId: PERSONAL_WORKSPACE_ID,
      sourceChannelIds: [CHANNEL_IDS.vkNews, CHANNEL_IDS.tgNews, CHANNEL_IDS.maxBrand],
      targetChannelIds: [
        CHANNEL_IDS.vkNews,
        CHANNEL_IDS.tgNews,
        CHANNEL_IDS.okBrand,
        CHANNEL_IDS.maxBrand,
      ],
      eventConsumers: ['crosspost'],
    },
    rules: [rule],
    items: [
      {
        id: ITEM_IDS.published1,
        ruleId: RULE_IDS.vkToTgMax,
        sourceChannelId: CHANNEL_IDS.vkNews,
        sourceRemoteId: 'wall-123456_10',
        sourceNetwork: 'vk',
        status: 'published',
        reasons: [],
        postId: '019a0000-post-7000-8000-000000000101',
        createdAt: minutesAgo(30),
        updatedAt: minutesAgo(25),
      },
      {
        id: ITEM_IDS.failed1,
        ruleId: RULE_IDS.vkToTgMax,
        sourceChannelId: CHANNEL_IDS.vkNews,
        sourceRemoteId: 'wall-123456_11',
        sourceNetwork: 'vk',
        status: 'failed',
        reasons: ['publish_error'],
        postId: null,
        createdAt: minutesAgo(90),
        updatedAt: minutesAgo(80),
      },
      {
        id: ITEM_IDS.filtered1,
        ruleId: RULE_IDS.vkToTgMax,
        sourceChannelId: CHANNEL_IDS.vkNews,
        sourceRemoteId: 'wall-123456_12',
        sourceNetwork: 'vk',
        status: 'filtered_out',
        reasons: ['exclude_ads', 'keyword_exclude'],
        postId: null,
        createdAt: minutesAgo(120),
        updatedAt: minutesAgo(120),
      },
    ],
  };
}

let state = createInitialState();

export function resetCrosspostStore(): void {
  state = createInitialState();
}

export function getCrosspostDemoSeed(): CrosspostDemoSeed {
  return state.seed;
}

export function listCrosspostRules(params?: {
  workspaceId?: string;
  projectId?: string;
}): CrosspostRule[] {
  return state.rules.filter((rule) => {
    if (params?.workspaceId && rule.workspaceId !== params.workspaceId) return false;
    if (params?.projectId && rule.projectId !== params.projectId) return false;
    return true;
  });
}

export function getCrosspostRule(ruleId: string): CrosspostRule | undefined {
  return state.rules.find((rule) => rule.id === ruleId);
}

function withWarnings(rule: CrosspostRule): CrosspostRule {
  const warnings = detectCycleWarnings(
    state.rules.filter((r) => r.id !== rule.id),
    {
      sourceChannelId: rule.sourceChannelId ?? '',
      targetChannelIds: (rule.targets ?? [])
        .map((t) => t.targetChannelId)
        .filter((id): id is string => Boolean(id)),
    },
  );
  // Also include edges from this rule itself when active/draft for UI review.
  const selfWarnings = detectCycleWarnings(state.rules, undefined).filter((w) =>
    (w.channelPath ?? []).includes(rule.sourceChannelId ?? ''),
  );
  const merged = [...warnings, ...selfWarnings].filter(
    (w, index, arr) => arr.findIndex((x) => x.code === w.code && JSON.stringify(x.channelPath) === JSON.stringify(w.channelPath)) === index,
  );
  return { ...rule, warnings: merged };
}

export function createCrosspostRule(
  workspaceId: string,
  projectId: string,
  body: CrosspostRuleCreate,
): CrosspostRule | CrosspostMutationError {
  if (!body.name?.trim() || !body.sourceChannelId) {
    return { status: 400, code: 'validation_failed', detail: 'name and sourceChannelId required' };
  }
  const source = getChannel(body.sourceChannelId);
  if (!source || source.workspaceId !== workspaceId) {
    return { status: 404, code: 'not_found', detail: 'Source channel not found' };
  }
  if (!['vk', 'tg', 'max'].includes(source.network ?? '')) {
    return { status: 400, code: 'validation_failed', detail: 'Unsupported source network' };
  }

  const existing = listCrosspostRules({ workspaceId, projectId });
  if (existing.length >= CROSSPOST_RULE_LIMIT) {
    return {
      status: 402,
      code: PLAN_LIMIT_EXCEEDED_CODE,
      detail: 'Plan limit for crosspost_rules exceeded',
    };
  }

  const targetIds = (body.targets ?? []).map((t) => t.targetChannelId);
  if (targetIds.includes(body.sourceChannelId)) {
    return { status: 400, code: 'validation_failed', detail: 'Source cannot be a target' };
  }
  if (targetIds.length === 0) {
    return { status: 400, code: 'validation_failed', detail: 'At least one target required' };
  }

  const targets: CrosspostTarget[] = [];
  for (const req of body.targets ?? []) {
    const channel = getChannel(req.targetChannelId);
    if (!channel || channel.workspaceId !== workspaceId) {
      return { status: 404, code: 'not_found', detail: 'Target channel not found' };
    }
    if (!['vk', 'ok', 'tg', 'max'].includes(channel.network ?? '')) {
      return { status: 400, code: 'validation_failed', detail: 'Unsupported target network' };
    }
    state.seq += 1;
    targets.push({
      id: nextId('xptgt', state.seq),
      targetChannelId: channel.id,
      network: channel.network,
      options: (req.options as Record<string, never>) ?? {},
    });
  }

  state.seq += 1;
  const now = nowIso();
  const activate = Boolean(body.activate);
  const rule: CrosspostRule = {
    id: nextId('xpost', state.seq),
    workspaceId,
    projectId,
    sourceChannelId: body.sourceChannelId,
    sourceNetwork: source.network,
    name: body.name.trim(),
    status: activate ? 'active' : 'draft',
    config: (body.config ? { ...defaultConfig(), ...body.config } : defaultConfig()) as never,
    configVersion: 1,
    version: 1,
    targets,
    createdAt: now,
    updatedAt: now,
  };

  state.rules.unshift(rule);
  return withWarnings(rule);
}

export function patchCrosspostRule(
  ruleId: string,
  body: CrosspostRulePatch,
): CrosspostRule | CrosspostMutationError {
  const rule = getCrosspostRule(ruleId);
  if (!rule) {
    return { status: 404, code: 'not_found', detail: 'Rule not found' };
  }
  if (body.version != null && body.version !== rule.version) {
    return { status: 409, code: 'optimistic_lock_conflict', detail: 'Rule version mismatch' };
  }

  let targets = rule.targets;
  if (body.targets) {
    const next: CrosspostTarget[] = [];
    for (const req of body.targets) {
      const channel = getChannel(req.targetChannelId);
      if (!channel) {
        return { status: 404, code: 'not_found', detail: 'Target channel not found' };
      }
      if (req.targetChannelId === (body as { sourceChannelId?: string }).sourceChannelId ||
          req.targetChannelId === rule.sourceChannelId) {
        return { status: 400, code: 'validation_failed', detail: 'Source cannot be a target' };
      }
      state.seq += 1;
      next.push({
        id: nextId('xptgt', state.seq),
        targetChannelId: channel.id,
        network: channel.network,
        options: (req.options as Record<string, never>) ?? {},
      });
    }
    targets = next;
  }

  const updated: CrosspostRule = {
    ...rule,
    name: body.name?.trim() ?? rule.name,
    config: (body.config
      ? { ...defaultConfig(), ...(rule.config as object), ...body.config }
      : rule.config) as never,
    configVersion: body.config ? (rule.configVersion ?? 1) + 1 : rule.configVersion,
    targets,
    version: (rule.version ?? 1) + 1,
    updatedAt: nowIso(),
  };
  state.rules = state.rules.map((item) => (item.id === ruleId ? updated : item));
  return withWarnings(updated);
}

export function activateCrosspostRule(ruleId: string): CrosspostRule | CrosspostMutationError {
  const rule = getCrosspostRule(ruleId);
  if (!rule) return { status: 404, code: 'not_found', detail: 'Rule not found' };
  const updated: CrosspostRule = {
    ...rule,
    status: 'active',
    updatedAt: nowIso(),
    config: (() => {
      const cfg = { ...(rule.config as Record<string, unknown>) };
      delete cfg.auto_pause_reason;
      delete cfg.auto_paused_at;
      return cfg as never;
    })(),
  };
  state.rules = state.rules.map((item) => (item.id === ruleId ? updated : item));
  return withWarnings(updated);
}

export function pauseCrosspostRule(ruleId: string): CrosspostRule | CrosspostMutationError {
  const rule = getCrosspostRule(ruleId);
  if (!rule) return { status: 404, code: 'not_found', detail: 'Rule not found' };
  const updated: CrosspostRule = { ...rule, status: 'paused', updatedAt: nowIso() };
  state.rules = state.rules.map((item) => (item.id === ruleId ? updated : item));
  return withWarnings(updated);
}

export function listCrosspostItems(
  ruleId: string,
  status?: string,
  cursor?: string,
  limit = 50,
): { items: CrosspostItem[]; nextCursor: string | null } {
  const filtered = state.items
    .filter((item) => {
      if (item.ruleId !== ruleId) return false;
      if (status && item.status !== status) return false;
      return true;
    })
    .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));

  let start = 0;
  if (cursor) {
    const idx = filtered.findIndex((item) => item.id === cursor);
    start = idx >= 0 ? idx + 1 : 0;
  }
  const slice = filtered.slice(start, start + limit);
  const last = slice[slice.length - 1];
  const nextCursor =
    last && start + slice.length < filtered.length ? (last.id ?? null) : null;
  return { items: slice, nextCursor };
}

export function previewCrosspostRule(
  ruleId: string,
  body: CrosspostPreviewRequest,
): CrosspostPreviewResponse | CrosspostMutationError {
  const rule = getCrosspostRule(ruleId);
  if (!rule) return { status: 404, code: 'not_found', detail: 'Rule not found' };

  const remote = body.sourceRemoteId ?? body.sourcePostUrl ?? 'preview-1';
  const text = `Превью кросспоста (${remote})`;
  const filterReasons: string[] = [];
  const cfg = (rule.config ?? {}) as Record<string, unknown>;
  const filters = (cfg.filters ?? {}) as Record<string, unknown>;
  if (filters.exclude_ads === true && String(remote).includes('ad')) {
    filterReasons.push('exclude_ads');
  }

  const targets = (rule.targets ?? []).map((target) => ({
    targetChannelId: target.targetChannelId,
    network: target.network,
    status: filterReasons.length > 0 ? 'skipped' : 'ok',
    reason: filterReasons[0],
    variant: {
      text,
      mediaCount: 1,
    } as never,
  }));

  return { filterReasons, targets };
}

export function retryCrosspostItem(itemId: string): CrosspostItem | CrosspostMutationError {
  const item = state.items.find((entry) => entry.id === itemId);
  if (!item) return { status: 404, code: 'not_found', detail: 'Item not found' };
  if (item.status !== 'failed') {
    return { status: 409, code: 'conflict', detail: 'Only failed items can be retried' };
  }
  const updated: CrosspostItem = {
    ...item,
    status: 'queued',
    reasons: ['retry_requested'],
    updatedAt: nowIso(),
  };
  state.items = state.items.map((entry) => (entry.id === itemId ? updated : entry));
  // Simulate success shortly in MSW by flipping to published.
  const published: CrosspostItem = {
    ...updated,
    status: 'published',
    reasons: [],
    postId: nextId('post', ++state.seq),
    updatedAt: nowIso(),
  };
  state.items = state.items.map((entry) => (entry.id === itemId ? published : entry));
  return published;
}

function isMutationError(value: unknown): value is CrosspostMutationError {
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
    instance: '/api/v1/crosspost',
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

function mutationResponse(result: CrosspostRule | CrosspostItem | CrosspostPreviewResponse | CrosspostMutationError) {
  if (isMutationError(result)) {
    return problem(result.status, result.code, result.detail, result.code);
  }
  return HttpResponse.json(result);
}

/** Full Crosspost handlers — spread into handlers.ts. */
export const crosspostHandlers: HttpHandler[] = [
  http.get(
    '/api/v1/workspaces/:workspaceId/projects/:projectId/crosspost/rules',
    ({ request, params }) => {
      const denied = requirePermission(request, 'automations.view');
      if (denied) return denied;
      const items = listCrosspostRules({
        workspaceId: String(params.workspaceId),
        projectId: String(params.projectId),
      }).map((rule) => withWarnings(rule));
      return HttpResponse.json({ items });
    },
  ),

  http.post(
    '/api/v1/workspaces/:workspaceId/projects/:projectId/crosspost/rules',
    async ({ request, params }) => {
      const denied = requirePermission(request, 'automations.manage');
      if (denied) return denied;
      const body = (await request.json()) as CrosspostRuleCreate;
      const result = createCrosspostRule(
        String(params.workspaceId),
        String(params.projectId),
        body,
      );
      if (isMutationError(result)) return mutationResponse(result);
      return HttpResponse.json(result, { status: 201 });
    },
  ),

  http.get('/api/v1/crosspost/rules/:ruleId', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.view');
    if (denied) return denied;
    const rule = getCrosspostRule(String(params.ruleId));
    if (!rule) return problem(404, 'not_found', 'Rule not found');
    return HttpResponse.json(withWarnings(rule));
  }),

  http.patch('/api/v1/crosspost/rules/:ruleId', async ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    const body = (await request.json()) as CrosspostRulePatch;
    return mutationResponse(patchCrosspostRule(String(params.ruleId), body));
  }),

  http.post('/api/v1/crosspost/rules/:ruleId/activate', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    return mutationResponse(activateCrosspostRule(String(params.ruleId)));
  }),

  http.post('/api/v1/crosspost/rules/:ruleId/pause', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.manage');
    if (denied) return denied;
    return mutationResponse(pauseCrosspostRule(String(params.ruleId)));
  }),

  http.get('/api/v1/crosspost/rules/:ruleId/items', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.view');
    if (denied) return denied;
    if (!getCrosspostRule(String(params.ruleId))) {
      return problem(404, 'not_found', 'Rule not found');
    }
    const url = new URL(request.url);
    const status = url.searchParams.get('status') ?? undefined;
    const cursor = url.searchParams.get('cursor') ?? undefined;
    const limitRaw = url.searchParams.get('limit');
    const limit = limitRaw ? Number(limitRaw) : 50;
    return HttpResponse.json(
      listCrosspostItems(String(params.ruleId), status, cursor, Number.isFinite(limit) ? limit : 50),
    );
  }),

  http.post('/api/v1/crosspost/rules/:ruleId/preview', async ({ request, params }) => {
    const denied = requirePermission(request, 'automations.view');
    if (denied) return denied;
    const body = (await request.json()) as CrosspostPreviewRequest;
    return mutationResponse(previewCrosspostRule(String(params.ruleId), body));
  }),

  http.post('/api/v1/crosspost/items/:itemId/retry', ({ request, params }) => {
    const denied = requirePermission(request, 'automations.moderate');
    if (denied) return denied;
    return mutationResponse(retryCrosspostItem(String(params.itemId)));
  }),
];
