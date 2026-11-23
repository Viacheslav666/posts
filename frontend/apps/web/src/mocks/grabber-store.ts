import type {
  GrabberBulkModerationRequest,
  GrabberBulkModerationResponse,
  GrabberConfig,
  GrabberItem,
  GrabberItemEditApproveRequest,
  GrabberRule,
  GrabberRuleCreate,
  GrabberRulePatch,
  GrabberRun,
  IngestPreviewRequest,
  IngestPreviewResponse,
} from '@posts/api-client';

import { isPrivateOrMetadataUrl } from '@/features/grabber/model/config';
import { normalizeSourceIds } from '@/features/grabber/model/source-ids';
import type { ItemStatus, RuleStatus } from '@/features/grabber/model/types';

import { CHANNEL_IDS } from './channel-store';
import {
  DEFAULT_PROJECT_ID,
  DEMO_USER_ID,
  PERSONAL_WORKSPACE_ID,
} from './workspace-store';

export const RULE_IDS = {
  vkActive: '019a0000-grab-7000-8000-000000000001',
  rssModerate: '019a0000-grab-7000-8000-000000000002',
} as const;

export const ITEM_IDS = {
  pending1: '019a0000-gitem-7000-8000-000000000001',
  pending2: '019a0000-gitem-7000-8000-000000000002',
  published1: '019a0000-gitem-7000-8000-000000000003',
} as const;

type GrabberStoreState = {
  rules: GrabberRule[];
  runs: GrabberRun[];
  items: GrabberItem[];
  lastManualRunAt: Record<string, number>;
  websiteEnabled: boolean;
  seq: number;
};

function nowIso(): string {
  return new Date().toISOString();
}

function nextId(prefix: string, seq: number): string {
  return `019a0000-${prefix}-7000-8000-${String(seq).padStart(12, '0')}`;
}

function defaultConfig(overrides?: Partial<GrabberConfig>): GrabberConfig {
  return {
    sourceIds: ['-123456'],
    scanIntervalMinutes: 180,
    publishMode: 'moderate',
    scheduleMode: 'immediate',
    publishDelayMinutes: 0,
    filters: {
      exclude_ads: true,
      exclude_reposts: true,
      keyword_include: [],
      keyword_exclude: [],
    } as unknown as GrabberConfig['filters'],
    attribution: {
      enabled: true,
      placement: 'footer',
      template: 'Источник: {source_name} — {source_url}',
    } as unknown as GrabberConfig['attribution'],
    limits: {
      rate_limit_per_hour: 10,
      rate_limit_per_day: 50,
    } as unknown as GrabberConfig['limits'],
    reliability: {
      dedup_enabled: true,
      dedup_scope: 'channel',
      ignore_edits: true,
      auto_pause_on_errors: true,
      auto_pause_error_threshold: 5,
    } as unknown as GrabberConfig['reliability'],
    ...overrides,
  };
}

function createInitialState(): GrabberStoreState {
  const now = nowIso();
  return {
    seq: 100,
    websiteEnabled: true,
    lastManualRunAt: {},
    rules: [
      {
        id: RULE_IDS.vkActive,
        workspaceId: PERSONAL_WORKSPACE_ID,
        projectId: DEFAULT_PROJECT_ID,
        type: 'grabber',
        name: 'VK → новости',
        targetChannelId: CHANNEL_IDS.vkNews,
        status: 'active',
        config: defaultConfig({ sourceIds: ['-123456', 'club_demo'] }),
        configVersion: 1,
        userResponsibilityAcceptedAt: now,
        userResponsibilityAcceptedBy: DEMO_USER_ID,
        lastRunAt: now,
        nextRunAt: new Date(Date.now() + 3 * 60 * 60 * 1000).toISOString(),
        consecutiveFailures: 0,
        createdBy: DEMO_USER_ID,
        createdAt: now,
        updatedAt: now,
        version: 1,
        copiedTotal: 2,
      },
      {
        id: RULE_IDS.rssModerate,
        workspaceId: PERSONAL_WORKSPACE_ID,
        projectId: DEFAULT_PROJECT_ID,
        type: 'rss',
        name: 'RSS блог',
        targetChannelId: CHANNEL_IDS.vkNews,
        status: 'paused',
        config: defaultConfig({
          sourceIds: undefined,
          rssUrl: 'https://example.com/feed.xml',
          rssMaxItemsPerRun: 20,
        }),
        configVersion: 1,
        userResponsibilityAcceptedAt: now,
        userResponsibilityAcceptedBy: DEMO_USER_ID,
        consecutiveFailures: 0,
        createdBy: DEMO_USER_ID,
        createdAt: now,
        updatedAt: now,
        version: 1,
        copiedTotal: 0,
      },
    ],
    runs: [],
    items: [
      {
        id: ITEM_IDS.pending1,
        ruleId: RULE_IDS.vkActive,
        runId: '019a0000-grun-7000-8000-000000000001',
        workspaceId: PERSONAL_WORKSPACE_ID,
        sourceNetwork: 'vk',
        sourceId: '-123456',
        remotePostId: 'wall-123456_10',
        sourcePermalink: 'https://vk.com/wall-123456_10',
        sourceName: 'Demo Source',
        contentText:
          'Черновик из граббера: анонс мероприятия.\n\nИсточник: Demo Source — https://vk.com/wall-123456_10',
        contentHash: 'a'.repeat(64),
        status: 'pending_moderation',
        detectedAt: now,
      },
      {
        id: ITEM_IDS.pending2,
        ruleId: RULE_IDS.vkActive,
        runId: '019a0000-grun-7000-8000-000000000001',
        workspaceId: PERSONAL_WORKSPACE_ID,
        sourceNetwork: 'vk',
        sourceId: '-123456',
        remotePostId: 'wall-123456_11',
        sourcePermalink: 'https://vk.com/wall-123456_11',
        sourceName: 'Demo Source',
        contentText:
          'Второй материал в очереди модерации.\n\nИсточник: Demo Source — https://vk.com/wall-123456_11',
        contentHash: 'b'.repeat(64),
        status: 'pending_moderation',
        detectedAt: now,
      },
      {
        id: ITEM_IDS.published1,
        ruleId: RULE_IDS.vkActive,
        runId: '019a0000-grun-7000-8000-000000000001',
        workspaceId: PERSONAL_WORKSPACE_ID,
        sourceNetwork: 'vk',
        sourceId: 'club_demo',
        remotePostId: 'wall-999_1',
        sourcePermalink: 'https://vk.com/wall-999_1',
        sourceName: 'Club Demo',
        contentText: 'Уже опубликованный item',
        contentHash: 'c'.repeat(64),
        status: 'published',
        postId: '019a0000-post-7000-8000-000000000099',
        detectedAt: now,
        moderatedAt: now,
        moderatedBy: DEMO_USER_ID,
      },
    ],
  };
}

let state = createInitialState();

export function resetGrabberStore(): void {
  state = createInitialState();
}

export function setWebsiteGrabberEnabled(enabled: boolean): void {
  state.websiteEnabled = enabled;
}

export function isWebsiteGrabberEnabled(): boolean {
  return state.websiteEnabled;
}

function findRule(ruleId: string): GrabberRule | undefined {
  return state.rules.find((rule) => rule.id === ruleId);
}

function assertTargetMode(
  config: GrabberConfig | undefined,
  type: GrabberRule['type'],
): string | null {
  const mode = config?.targetMode ?? 'wall_now';
  const limits = (config?.limits ?? {}) as Record<string, unknown>;
  const interval = typeof limits.min_interval_minutes === 'number' ? limits.min_interval_minutes : 0;
  const postponed = mode === 'vk_postponed_post' || mode === 'vk_postponed_repost';
  if (postponed && interval > 0) {
    return 'Интервал между постами недоступен в отложенных записях VK. Укажите 0 или выберите публикацию сразу на стену';
  }
  if ((mode === 'wall_repost' || mode === 'vk_postponed_repost') && type && type !== 'grabber') {
    return 'Репост доступен только для граббера VK';
  }
  const perDay = limits.rate_limit_per_day;
  const perSource = limits.rate_limit_per_source_per_day;
  if (typeof perDay === 'number' && (perDay < 0 || perDay > 500)) {
    return 'Суточный лимит по всем источникам: 0 — не ограничивать, максимум 500';
  }
  if (typeof perSource === 'number' && (perSource < 0 || perSource > 500)) {
    return 'Суточный лимит на источник: 0 — не ограничивать, максимум 500';
  }
  return null;
}

function assertAttribution(config: GrabberConfig | undefined): string | null {
  const attribution = (config?.attribution ?? {}) as Record<string, unknown>;
  if (attribution.enabled === false) {
    return 'ingest_attribution_required';
  }
  const template = typeof attribution.template === 'string' ? attribution.template.trim() : '';
  if (!template) {
    return 'ingest_attribution_required';
  }
  return null;
}

function assertSsrf(config: GrabberConfig | undefined, type: GrabberRule['type']): string | null {
  if (type === 'rss' && config?.rssUrl && isPrivateOrMetadataUrl(config.rssUrl)) {
    return 'ingest_ssrf_blocked';
  }
  if (type === 'website') {
    for (const url of config?.websiteUrls ?? []) {
      if (isPrivateOrMetadataUrl(url)) {
        return 'ingest_ssrf_blocked';
      }
    }
  }
  return null;
}

function withPublishModeDefault(config: GrabberConfig | undefined): GrabberConfig {
  const next = { ...(config ?? {}) };
  if (!next.publishMode) {
    next.publishMode = 'moderate';
  }
  if (!next.targetMode) {
    next.targetMode = 'wall_now';
  }
  if (next.attribution == null) {
    next.attribution = {
      enabled: true,
      placement: 'footer',
      template: 'Источник: {source_name} — {source_url}',
    } as unknown as GrabberConfig['attribution'];
  }
  return next;
}

export function listRules(params: {
  workspaceId: string;
  projectId: string;
  type?: string;
}): GrabberRule[] {
  return state.rules.filter((rule) => {
    if (rule.workspaceId !== params.workspaceId) return false;
    if (rule.projectId !== params.projectId) return false;
    if (rule.status === 'archived') return false;
    if (params.type && rule.type !== params.type) return false;
    return true;
  });
}

export function getRule(ruleId: string): GrabberRule | undefined {
  return findRule(ruleId);
}

export type GrabberMutationError = {
  status: number;
  code: string;
  detail: string;
};

export function createRule(
  workspaceId: string,
  projectId: string,
  body: GrabberRuleCreate,
): GrabberRule | GrabberMutationError {
  if (body.type === 'website' && !state.websiteEnabled) {
    return { status: 404, code: 'not_found', detail: 'Website grabber is disabled' };
  }
  if (!body.userResponsibilityAccepted && body.activate) {
    return {
      status: 422,
      code: 'ingest_responsibility_required',
      detail: 'Подтвердите ответственность за контент',
    };
  }
  const config = withPublishModeDefault(body.config);
  const modeError = assertTargetMode(config, body.type);
  if (modeError) {
    return { status: 422, code: 'validation_failed', detail: modeError };
  }
  const ssrf = assertSsrf(config, body.type);
  if (ssrf) {
    return { status: 422, code: ssrf, detail: 'URL источника указывает на закрытую сеть (SSRF)' };
  }
  if (body.activate) {
    const attributionError = assertAttribution(config);
    if (attributionError) {
      return {
        status: 422,
        code: attributionError,
        detail: 'Атрибуция источника обязательна',
      };
    }
    if (!body.userResponsibilityAccepted) {
      return {
        status: 422,
        code: 'ingest_responsibility_required',
        detail: 'Подтвердите ответственность за контент',
      };
    }
  }

  if (body.type === 'grabber') {
    config.sourceIds = normalizeSourceIds((config.sourceIds ?? []).join(','));
  }

  state.seq += 1;
  const now = nowIso();
  const rule: GrabberRule = {
    id: nextId('grab', state.seq),
    workspaceId,
    projectId,
    type: body.type,
    name: body.name,
    targetChannelId: body.targetChannelId,
    status: body.activate ? 'active' : 'draft',
    config,
    configVersion: 1,
    userResponsibilityAcceptedAt: body.userResponsibilityAccepted ? now : undefined,
    userResponsibilityAcceptedBy: body.userResponsibilityAccepted ? DEMO_USER_ID : undefined,
    consecutiveFailures: 0,
    createdBy: DEMO_USER_ID,
    createdAt: now,
    updatedAt: now,
    version: 1,
    copiedTotal: 0,
    nextRunAt: body.activate
      ? new Date(Date.now() + (config.scanIntervalMinutes ?? 180) * 60_000).toISOString()
      : undefined,
  };
  state.rules.unshift(rule);
  return rule;
}

export function patchRule(ruleId: string, body: GrabberRulePatch): GrabberRule | GrabberMutationError {
  const rule = findRule(ruleId);
  if (!rule) {
    return { status: 404, code: 'not_found', detail: 'Rule not found' };
  }
  if (rule.version !== body.version) {
    return { status: 409, code: 'optimistic_lock_conflict', detail: 'Rule version conflict' };
  }
  const nextConfig = body.config ? withPublishModeDefault(body.config) : rule.config;
  const modeError = assertTargetMode(nextConfig, rule.type);
  if (modeError) {
    return { status: 422, code: 'validation_failed', detail: modeError };
  }
  if (rule.type === 'website' && !state.websiteEnabled) {
    return { status: 404, code: 'not_found', detail: 'Website grabber is disabled' };
  }
  const ssrf = assertSsrf(nextConfig, rule.type);
  if (ssrf) {
    return { status: 422, code: ssrf, detail: 'URL источника указывает на закрытую сеть (SSRF)' };
  }
  const now = nowIso();
  const updated: GrabberRule = {
    ...rule,
    name: body.name ?? rule.name,
    targetChannelId: body.targetChannelId ?? rule.targetChannelId,
    config: nextConfig,
    configVersion: (rule.configVersion ?? 1) + (body.config ? 1 : 0),
    version: (rule.version ?? 1) + 1,
    updatedAt: now,
  };
  state.rules = state.rules.map((item) => (item.id === ruleId ? updated : item));
  return updated;
}

function setStatus(ruleId: string, status: RuleStatus): GrabberRule | GrabberMutationError {
  const rule = findRule(ruleId);
  if (!rule) {
    return { status: 404, code: 'not_found', detail: 'Rule not found' };
  }
  const updated: GrabberRule = {
    ...rule,
    status,
    updatedAt: nowIso(),
    version: (rule.version ?? 1) + 1,
  };
  state.rules = state.rules.map((item) => (item.id === ruleId ? updated : item));
  return updated;
}

export function activateRule(ruleId: string): GrabberRule | GrabberMutationError {
  const rule = findRule(ruleId);
  if (!rule) {
    return { status: 404, code: 'not_found', detail: 'Rule not found' };
  }
  const attributionError = assertAttribution(rule.config);
  if (attributionError) {
    return { status: 422, code: attributionError, detail: 'Атрибуция источника обязательна' };
  }
  if (!rule.userResponsibilityAcceptedAt) {
    return {
      status: 422,
      code: 'ingest_responsibility_required',
      detail: 'Подтвердите ответственность за контент',
    };
  }
  return setStatus(ruleId, 'active');
}

export function pauseRule(ruleId: string): GrabberRule | GrabberMutationError {
  return setStatus(ruleId, 'paused');
}

export function resumeRule(ruleId: string): GrabberRule | GrabberMutationError {
  return setStatus(ruleId, 'active');
}

export function archiveRule(ruleId: string): GrabberRule | GrabberMutationError {
  return setStatus(ruleId, 'archived');
}

export function startRun(ruleId: string): GrabberRun | GrabberMutationError {
  const rule = findRule(ruleId);
  if (!rule) {
    return { status: 404, code: 'not_found', detail: 'Rule not found' };
  }
  if (rule.status !== 'active') {
    return { status: 409, code: 'conflict', detail: 'Rule is not active' };
  }
  const last = state.lastManualRunAt[ruleId] ?? 0;
  if (Date.now() - last < 5 * 60 * 1000 && last > 0) {
    return {
      status: 429,
      code: 'ingest_rate_limited',
      detail: 'Ручной запуск доступен не чаще одного раза в 5 минут',
    };
  }
  state.lastManualRunAt[ruleId] = Date.now();
  state.seq += 1;
  const startedAt = nowIso();
  const run: GrabberRun = {
    id: nextId('grun', state.seq),
    ruleId,
    workspaceId: rule.workspaceId,
    trigger: 'manual',
    status: 'succeeded',
    startedAt,
    finishedAt: startedAt,
    sourcesScanned: rule.config?.sourceIds?.length ?? 1,
    itemsFound: 1,
    itemsNew: 1,
    itemsDeduped: 0,
    itemsPublished: 0,
    traceId: `msw-trace-${state.seq}`,
  };
  state.runs.unshift(run);

  const publishMode = rule.config?.publishMode ?? 'moderate';
  state.seq += 1;
  const item: GrabberItem = {
    id: nextId('gitem', state.seq),
    ruleId,
    runId: run.id,
    workspaceId: rule.workspaceId!,
    sourceNetwork: rule.type === 'rss' ? 'rss' : rule.type === 'website' ? 'website' : 'vk',
    sourceId: rule.config?.sourceIds?.[0] ?? rule.config?.rssUrl ?? 'site',
    remotePostId: `remote-${state.seq}`,
    sourcePermalink: 'https://vk.com/wall-123456_99',
    sourceName: 'Live check',
    contentText: `Новый материал от ручной проверки #${state.seq}`,
    contentHash: `${state.seq.toString(16).padStart(64, '0')}`,
    status: publishMode === 'moderate' ? 'pending_moderation' : 'approved',
    detectedAt: startedAt,
    postId: publishMode === 'auto' ? nextId('post', state.seq) : undefined,
  };
  state.items.unshift(item);

  const updatedRule: GrabberRule = {
    ...rule,
    lastRunAt: startedAt,
    nextRunAt: new Date(
      Date.now() + (rule.config?.scanIntervalMinutes ?? 180) * 60_000,
    ).toISOString(),
    copiedTotal: (rule.copiedTotal ?? 0) + 1,
    updatedAt: startedAt,
  };
  state.rules = state.rules.map((itemRule) => (itemRule.id === ruleId ? updatedRule : itemRule));
  return run;
}

export function listRuns(ruleId: string): GrabberRun[] {
  return state.runs.filter((run) => run.ruleId === ruleId);
}

export function listItems(ruleId: string, status?: ItemStatus): GrabberItem[] {
  return state.items.filter((item) => {
    if (item.ruleId !== ruleId) return false;
    if (status && item.status !== status) return false;
    return true;
  });
}

export function listQueueItems(workspaceId: string, status?: ItemStatus): GrabberItem[] {
  return state.items.filter((item) => {
    if (item.workspaceId !== workspaceId) return false;
    if (status) return item.status === status;
    return item.status === 'pending_moderation';
  });
}

export function getItem(itemId: string): GrabberItem | undefined {
  return state.items.find((item) => item.id === itemId);
}

function moderateItem(
  itemId: string,
  status: ItemStatus,
  overrides?: Partial<GrabberItem>,
): GrabberItem | GrabberMutationError {
  const item = getItem(itemId);
  if (!item) {
    return { status: 404, code: 'not_found', detail: 'Item not found' };
  }
  if (item.status !== 'pending_moderation' && status !== item.status) {
    if (item.status === 'approved' || item.status === 'published' || item.status === 'scheduled') {
      return item;
    }
  }
  const updated: GrabberItem = {
    ...item,
    status,
    moderatedAt: nowIso(),
    moderatedBy: DEMO_USER_ID,
    postId:
      status === 'approved' || status === 'scheduled' || status === 'published'
        ? item.postId ?? nextId('post', ++state.seq)
        : item.postId,
    ...overrides,
  };
  // Simulate pipeline: approve → scheduled → published quickly in MSW.
  if (status === 'approved') {
    updated.status = 'published';
  }
  state.items = state.items.map((entry) => (entry.id === itemId ? updated : entry));
  return updated;
}

export function approveItem(itemId: string): GrabberItem | GrabberMutationError {
  return moderateItem(itemId, 'approved');
}

export function rejectItem(itemId: string, reason?: string): GrabberItem | GrabberMutationError {
  return moderateItem(itemId, 'rejected', { skipReason: reason });
}

export function editAndApproveItem(
  itemId: string,
  body: GrabberItemEditApproveRequest,
): GrabberItem | GrabberMutationError {
  return moderateItem(itemId, 'approved', { contentText: body.contentText });
}

export function bulkModerate(body: GrabberBulkModerationRequest): GrabberBulkModerationResponse {
  const failedIds: string[] = [];
  let succeeded = 0;
  for (const id of body.itemIds) {
    const result =
      body.action === 'approve' ? approveItem(id) : rejectItem(id, body.reason);
    if ('code' in result) {
      failedIds.push(id);
    } else {
      succeeded += 1;
    }
  }
  return { succeeded, failed: failedIds.length, failedIds };
}

export function cloneRuleConfig(
  ruleId: string,
  sourceRuleId: string,
): GrabberRule | GrabberMutationError {
  const target = findRule(ruleId);
  if (!target) {
    return { status: 404, code: 'not_found', detail: 'Rule not found' };
  }
  if (target.status === 'archived') {
    return { status: 409, code: 'conflict', detail: 'Rule is archived' };
  }
  const source = findRule(sourceRuleId);
  if (!source || source.workspaceId !== target.workspaceId) {
    return { status: 404, code: 'not_found', detail: 'Source rule not found' };
  }
  const sourceConfig = source.config ?? {};
  const nextConfig: GrabberConfig = {
    ...(target.config ?? {}),
    ...(sourceConfig.filters != null ? { filters: sourceConfig.filters } : {}),
    ...(sourceConfig.transform != null ? { transform: sourceConfig.transform } : {}),
    ...(sourceConfig.attribution != null ? { attribution: sourceConfig.attribution } : {}),
    ...(sourceConfig.limits != null ? { limits: sourceConfig.limits } : {}),
    ...(sourceConfig.reliability != null ? { reliability: sourceConfig.reliability } : {}),
    ...(sourceConfig.schedule != null ? { schedule: sourceConfig.schedule } : {}),
    ...(sourceConfig.firstComment != null ? { firstComment: sourceConfig.firstComment } : {}),
    ...(sourceConfig.macros != null ? { macros: sourceConfig.macros } : {}),
  };
  const updated: GrabberRule = {
    ...target,
    config: nextConfig,
    configVersion: (target.configVersion ?? 1) + 1,
    version: (target.version ?? 1) + 1,
    updatedAt: nowIso(),
  };
  state.rules = state.rules.map((item) => (item.id === ruleId ? updated : item));
  return updated;
}

export function exportItemsCsv(ruleId: string, status?: ItemStatus): string {
  const rows = listItems(ruleId, status);
  const header = [
    'group',
    'remote_post_id',
    'source_id',
    'post_id',
    'detected_at',
    'status',
    'skip_reason',
    'permalink',
  ];
  const rule = findRule(ruleId);
  const lines = [
    header.join(','),
    ...rows.map((item) =>
      [
        csvEscape(rule?.name ?? ''),
        csvEscape(item.remotePostId ?? ''),
        csvEscape(item.sourceId ?? ''),
        csvEscape(item.postId ?? ''),
        csvEscape(item.detectedAt ?? ''),
        csvEscape(item.status ?? ''),
        csvEscape(item.skipReason ?? ''),
        csvEscape(item.sourcePermalink ?? ''),
      ].join(','),
    ),
  ];
  return lines.join('\n');
}

function csvEscape(value: string): string {
  if (/[",\n]/.test(value)) {
    return `"${value.replace(/"/g, '""')}"`;
  }
  return value;
}

export function previewSource(
  workspaceId: string,
  body: IngestPreviewRequest,
): IngestPreviewResponse | GrabberMutationError {
  if (body.type === 'website' && !state.websiteEnabled) {
    return { status: 404, code: 'not_found', detail: 'Website grabber is disabled' };
  }
  if (body.type === 'rss' && body.rssUrl && isPrivateOrMetadataUrl(body.rssUrl)) {
    return {
      status: 422,
      code: 'ingest_ssrf_blocked',
      detail: 'URL источника указывает на закрытую сеть (SSRF)',
    };
  }
  if (body.type === 'website') {
    for (const url of body.websiteUrls ?? []) {
      if (isPrivateOrMetadataUrl(url)) {
        return {
          status: 422,
          code: 'ingest_ssrf_blocked',
          detail: 'URL источника указывает на закрытую сеть (SSRF)',
        };
      }
    }
  }
  const limit = body.limit ?? 3;
  const items: GrabberItem[] = Array.from({ length: Math.min(limit, 3) }, (_, index) => ({
    id: nextId('prev', state.seq + index + 1),
    ruleId: '00000000-0000-0000-0000-000000000000',
    workspaceId,
    sourceNetwork: body.type === 'rss' ? 'rss' : body.type === 'website' ? 'website' : 'vk',
    sourceId: body.sourceIds?.[0] ?? body.rssUrl ?? body.websiteUrls?.[0] ?? 'preview',
    remotePostId: `preview-${index + 1}`,
    sourcePermalink: 'https://example.com/preview',
    sourceName: 'Preview',
    contentText: `Предпросмотр материала #${index + 1}`,
    contentHash: `${(index + 1).toString(16).padStart(64, '0')}`,
    status: 'discovered',
    detectedAt: nowIso(),
  }));
  return { items };
}
