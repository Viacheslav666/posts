import type {
  Channel,
  ChannelCheckResponse,
  ChannelConnectCallbackRequest,
  ChannelConnectConfirmRequest,
  ChannelConnectStartResponse,
  ChannelSet,
  CreateChannelSetRequest,
  UpdateChannelRequest,
  UpdateChannelSetRequest,
} from '@posts/api-client';

import {
  ACME_WORKSPACE_ID,
  DEFAULT_PROJECT_ID,
  getWorkspace,
  PERSONAL_WORKSPACE_ID,
} from './workspace-store';

export const CHANNEL_IDS = {
  vkNews: '019a0000-chan-7000-8000-000000000001',
  tgNews: '019a0000-chan-7000-8000-000000000002',
  okBrand: '019a0000-chan-7000-8000-000000000003',
  maxBrand: '019a0000-chan-7000-8000-000000000004',
  vkShop: '019a0000-chan-7000-8000-000000000005',
  tgReauth: '019a0000-chan-7000-8000-000000000006',
} as const;

export const CHANNEL_SET_ID = '019a0000-cset-7000-8000-000000000001';

/** Plan channel quotas used by MSW (mirrors pricing Free/Start/Pro/Agency). */
export const PLAN_CHANNEL_LIMITS: Record<string, number> = {
  free: 3,
  start: 10,
  pro: 30,
  agency: 100,
};

export type ConnectNetwork = 'vk' | 'tg' | 'ok' | 'max' | 'rutube';

type PendingConnect = {
  network: ConnectNetwork;
  workspaceId: string;
  projectId?: string;
  state: string;
};

type ChannelStoreState = {
  channels: Channel[];
  sets: ChannelSet[];
  pending: PendingConnect[];
  seq: number;
};

function createInitialState(): ChannelStoreState {
  const now = new Date().toISOString();
  return {
    seq: 10,
    pending: [],
    sets: [
      {
        id: CHANNEL_SET_ID,
        workspaceId: PERSONAL_WORKSPACE_ID,
        name: 'Утренние посты',
        channelIds: [CHANNEL_IDS.vkNews, CHANNEL_IDS.tgNews],
      },
    ],
    channels: [
      {
        id: CHANNEL_IDS.vkNews,
        workspaceId: PERSONAL_WORKSPACE_ID,
        projectId: DEFAULT_PROJECT_ID,
        network: 'vk',
        type: 'group',
        title: 'Posts — новости',
        username: 'posts_news',
        status: 'active',
        timezone: 'Europe/Moscow',
        createdAt: now,
      },
      {
        id: CHANNEL_IDS.tgNews,
        workspaceId: PERSONAL_WORKSPACE_ID,
        projectId: DEFAULT_PROJECT_ID,
        network: 'tg',
        type: 'channel',
        title: '@posts_news',
        username: '@posts_news',
        status: 'active',
        timezone: 'Europe/Moscow',
        createdAt: now,
      },
      {
        id: CHANNEL_IDS.okBrand,
        workspaceId: PERSONAL_WORKSPACE_ID,
        projectId: DEFAULT_PROJECT_ID,
        network: 'ok',
        type: 'group',
        title: 'Posts OK',
        status: 'active',
        timezone: 'Europe/Moscow',
        createdAt: now,
      },
      {
        id: CHANNEL_IDS.maxBrand,
        workspaceId: PERSONAL_WORKSPACE_ID,
        projectId: DEFAULT_PROJECT_ID,
        network: 'max',
        type: 'chat',
        title: 'Posts Max',
        status: 'active',
        timezone: 'Europe/Moscow',
        createdAt: now,
      },
      {
        id: CHANNEL_IDS.vkShop,
        workspaceId: PERSONAL_WORKSPACE_ID,
        projectId: DEFAULT_PROJECT_ID,
        network: 'vk',
        type: 'group',
        title: 'VK Витрина',
        status: 'no_permissions',
        statusReason: 'Missing admin rights on the community',
        timezone: 'Europe/Moscow',
        createdAt: now,
      },
      {
        id: CHANNEL_IDS.tgReauth,
        workspaceId: PERSONAL_WORKSPACE_ID,
        projectId: DEFAULT_PROJECT_ID,
        network: 'tg',
        type: 'channel',
        title: '@posts_alerts',
        username: '@posts_alerts',
        status: 'reauth_required',
        statusReason: 'Bot was removed from the channel',
        timezone: 'Europe/Moscow',
        createdAt: now,
      },
      {
        id: '019a0000-chan-7000-8000-000000000007',
        workspaceId: ACME_WORKSPACE_ID,
        network: 'vk',
        type: 'group',
        title: 'Acme VK',
        status: 'active',
        timezone: 'Europe/Moscow',
        createdAt: now,
      },
    ],
  };
}

let state = createInitialState();
/** Optional per-workspace limit override (tests / demos). */
const limitOverrides = new Map<string, number>();

function nextId(prefix: string): string {
  state.seq += 1;
  return `019a0000-${prefix}-7000-8000-${String(state.seq).padStart(12, '0')}`;
}

export function resetChannelStore(): void {
  state = createInitialState();
  limitOverrides.clear();
}

export function setChannelLimitOverride(workspaceId: string, limit: number | null): void {
  if (limit === null) {
    limitOverrides.delete(workspaceId);
    return;
  }
  limitOverrides.set(workspaceId, limit);
}

export function channelLimitForWorkspace(workspaceId: string): number {
  const override = limitOverrides.get(workspaceId);
  if (typeof override === 'number') {
    return override;
  }
  const workspace = getWorkspace(workspaceId);
  const plan = workspace?.planCode ?? 'free';
  return PLAN_CHANNEL_LIMITS[plan] ?? PLAN_CHANNEL_LIMITS.free;
}

export function countActiveChannels(workspaceId: string): number {
  return state.channels.filter(
    (channel) => channel.workspaceId === workspaceId && channel.status !== 'disabled',
  ).length;
}

export function listChannels(
  workspaceId: string,
  filters?: {
    projectId?: string;
    network?: string;
    status?: string;
  },
): Channel[] {
  return state.channels.filter((channel) => {
    if (channel.workspaceId !== workspaceId) return false;
    if (filters?.projectId && channel.projectId !== filters.projectId) return false;
    if (filters?.network && channel.network !== filters.network) return false;
    if (filters?.status && channel.status !== filters.status) return false;
    return true;
  });
}

export function getChannel(channelId: string): Channel | undefined {
  return state.channels.find((channel) => channel.id === channelId);
}

export function startConnect(
  network: ConnectNetwork,
  workspaceId: string,
  projectId?: string,
): ChannelConnectStartResponse {
  const stateToken = `msw-${network}-${Date.now()}`;
  state.pending.push({ network, workspaceId, projectId, state: stateToken });

  if (network === 'tg' || network === 'max') {
    return {
      state: stateToken,
      botInviteUrl: `/app/${workspaceId}/channels?bot=${network}&state=${stateToken}`,
    };
  }

  // Local callback URL — no external OAuth host (required for offline next build / MSW).
  return {
    state: stateToken,
    authorizationUrl: `/app/${workspaceId}/channels/connect/${network}/callback?code=demo-oauth-code&state=${encodeURIComponent(stateToken)}${projectId ? `&projectId=${projectId}` : ''}`,
  };
}

function ensureWithinPlan(workspaceId: string): { error: 'plan_limit_exceeded' } | null {
  if (countActiveChannels(workspaceId) >= channelLimitForWorkspace(workspaceId)) {
    return { error: 'plan_limit_exceeded' };
  }
  return null;
}

function createChannelFromConnect(input: {
  network: ConnectNetwork;
  workspaceId: string;
  projectId?: string;
  title: string;
  username?: string;
  type: Channel['type'];
}): Channel | { error: 'plan_limit_exceeded' } {
  const limit = ensureWithinPlan(input.workspaceId);
  if (limit) return limit;

  const channel: Channel = {
    id: nextId('chan'),
    workspaceId: input.workspaceId,
    projectId: input.projectId,
    network: input.network,
    type: input.type,
    title: input.title,
    username: input.username,
    status: 'active',
    timezone: 'Europe/Moscow',
    createdAt: new Date().toISOString(),
  };
  state.channels = [...state.channels, channel];
  return channel;
}

export function connectCallback(
  network: ConnectNetwork,
  body: ChannelConnectCallbackRequest,
): Channel[] | { error: 'plan_limit_exceeded' | 'validation_failed' | 'insufficient_permissions' } {
  if (!body.workspaceId) {
    return { error: 'validation_failed' };
  }

  if (network === 'vk' || network === 'ok') {
    if (!body.code || !body.state) {
      return { error: 'validation_failed' };
    }
    const pending = state.pending.find(
      (item) => item.state === body.state && item.network === network,
    );
    if (!pending) {
      return { error: 'validation_failed' };
    }
    state.pending = state.pending.filter((item) => item.state !== body.state);
    const created = createChannelFromConnect({
      network,
      workspaceId: body.workspaceId,
      projectId: body.projectId ?? pending.projectId,
      title: network === 'vk' ? 'VK — новое сообщество' : 'OK — новая группа',
      type: 'group',
    });
    if ('error' in created) return created;
    return [created];
  }

  // Bot flows may also finish via callback with username.
  return confirmBotConnect(network, {
    workspaceId: body.workspaceId,
    projectId: body.projectId,
    username: body.username,
    chatId: body.chatId,
    forwardedMessage: body.forwardedMessage,
  });
}

export function confirmBotConnect(
  network: ConnectNetwork,
  body: ChannelConnectConfirmRequest,
): Channel[] | { error: 'plan_limit_exceeded' | 'validation_failed' | 'insufficient_permissions' } {
  if (network !== 'tg' && network !== 'max') {
    return { error: 'validation_failed' };
  }
  if (!body.workspaceId) {
    return { error: 'validation_failed' };
  }

  const username = body.username?.trim();
  if (!username && !body.chatId && !body.forwardedMessage) {
    return { error: 'validation_failed' };
  }

  if (username === '@denied' || username === 'denied') {
    return { error: 'insufficient_permissions' };
  }

  const normalized = username
    ? username.startsWith('@')
      ? username
      : `@${username}`
    : `@chat_${body.chatId ?? 'new'}`;

  const created = createChannelFromConnect({
    network,
    workspaceId: body.workspaceId,
    projectId: body.projectId,
    title: normalized,
    username: normalized,
    type: network === 'tg' ? 'channel' : 'chat',
  });
  if ('error' in created) return created;
  return [created];
}

export function updateChannel(
  channelId: string,
  body: UpdateChannelRequest,
): Channel | undefined {
  const index = state.channels.findIndex((channel) => channel.id === channelId);
  if (index < 0) return undefined;
  const current = state.channels[index]!;
  const next: Channel = {
    ...current,
    projectId: body.projectId ?? current.projectId,
    timezone: body.timezone ?? current.timezone,
  };
  state.channels = state.channels.map((channel, i) => (i === index ? next : channel));
  return next;
}

export function deleteChannel(channelId: string): boolean {
  const before = state.channels.length;
  state.channels = state.channels.filter((channel) => channel.id !== channelId);
  state.sets = state.sets.map((set) => ({
    ...set,
    channelIds: (set.channelIds ?? []).filter((id) => id !== channelId),
  }));
  return state.channels.length < before;
}

export function checkChannel(channelId: string): ChannelCheckResponse | undefined {
  const channel = getChannel(channelId);
  if (!channel) return undefined;

  // Re-check restores reauth_required → active for MSW demos.
  let status = channel.status ?? 'active';
  let statusReason = channel.statusReason;
  if (status === 'reauth_required') {
    status = 'active';
    statusReason = undefined;
    state.channels = state.channels.map((item) =>
      item.id === channelId ? { ...item, status, statusReason } : item,
    );
  }

  return {
    channelId,
    status,
    statusReason,
    checkedAt: new Date().toISOString(),
  };
}

export function listChannelSets(workspaceId: string): ChannelSet[] {
  return state.sets.filter((set) => set.workspaceId === workspaceId);
}

export function getChannelSet(setId: string): ChannelSet | undefined {
  return state.sets.find((set) => set.id === setId);
}

export function createChannelSet(
  workspaceId: string,
  body: CreateChannelSetRequest,
): ChannelSet | { error: 'validation_failed' } {
  if (!body.name?.trim()) {
    return { error: 'validation_failed' };
  }
  if ((body.channelIds?.length ?? 0) > 50) {
    return { error: 'validation_failed' };
  }
  const set: ChannelSet = {
    id: nextId('cset'),
    workspaceId,
    name: body.name.trim(),
    channelIds: body.channelIds ?? [],
  };
  state.sets = [...state.sets, set];
  return set;
}

export function updateChannelSet(
  setId: string,
  body: UpdateChannelSetRequest,
): ChannelSet | { error: 'validation_failed' } | undefined {
  const index = state.sets.findIndex((set) => set.id === setId);
  if (index < 0) return undefined;
  if (body.channelIds && body.channelIds.length > 50) {
    return { error: 'validation_failed' };
  }
  const current = state.sets[index]!;
  const next: ChannelSet = {
    ...current,
    name: body.name?.trim() || current.name,
    channelIds: body.channelIds ?? current.channelIds,
  };
  state.sets = state.sets.map((set, i) => (i === index ? next : set));
  return next;
}

export function deleteChannelSet(setId: string): boolean {
  const before = state.sets.length;
  state.sets = state.sets.filter((set) => set.id !== setId);
  return state.sets.length < before;
}
