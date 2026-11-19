import type { EventSubscriptionResponse } from '@posts/api-client';
import { http, HttpResponse, type HttpHandler } from 'msw';

import { can, type WorkspaceRole } from '@/shared/lib/permissions';
import { INSUFFICIENT_PERMISSIONS_CODE } from '@/shared/lib/errors';

import { CHANNEL_IDS } from './channel-store';
import { PERSONAL_WORKSPACE_ID } from './workspace-store';

function roleFromAuth(request: Request): WorkspaceRole {
  const auth = request.headers.get('Authorization') ?? '';
  const token = auth.replace(/^Bearer\s+/i, '');
  if (token.includes('viewer')) return 'viewer';
  if (token.includes('author')) return 'author';
  if (token.includes('editor')) return 'editor';
  if (token.includes('admin')) return 'admin';
  return 'owner';
}

function problem(
  status: number,
  code: string,
  detail: string,
  title: string,
  instance: string,
) {
  return HttpResponse.json(
    { type: 'about:blank', title, status, detail, instance, code },
    { status },
  );
}

type StoreState = {
  byChannelId: Map<string, EventSubscriptionResponse>;
};

function isoMinutesAgo(minutes: number): string {
  return new Date(Date.now() - minutes * 60_000).toISOString();
}

function createSeed(): Map<string, EventSubscriptionResponse> {
  const map = new Map<string, EventSubscriptionResponse>();
  map.set(CHANNEL_IDS.vkNews, {
    id: '019a0000-esub-7000-8000-000000000001',
    channelId: CHANNEL_IDS.vkNews,
    workspaceId: PERSONAL_WORKSPACE_ID,
    network: 'vk',
    mode: 'callback',
    status: 'failed',
    consumers: ['suggest'],
    eventTypes: ['wall_post_new', 'wall_reply_new'],
    lastEventAt: isoMinutesAgo(90),
    lastError: 'Callback server returned 401; secret mismatch',
    failureCount: 3,
    updatedAt: isoMinutesAgo(5),
  });
  map.set(CHANNEL_IDS.tgNews, {
    id: '019a0000-esub-7000-8000-000000000002',
    channelId: CHANNEL_IDS.tgNews,
    workspaceId: PERSONAL_WORKSPACE_ID,
    network: 'tg',
    mode: 'webhook',
    status: 'active',
    consumers: ['antispam', 'crosspost'],
    eventTypes: ['channel_post', 'message'],
    lastEventAt: isoMinutesAgo(12),
    failureCount: 0,
    updatedAt: isoMinutesAgo(12),
    warning: undefined,
  });
  map.set(CHANNEL_IDS.maxBrand, {
    id: '019a0000-esub-7000-8000-000000000003',
    channelId: CHANNEL_IDS.maxBrand,
    workspaceId: PERSONAL_WORKSPACE_ID,
    network: 'max',
    mode: 'webhook',
    status: 'pending',
    consumers: ['antispam'],
    eventTypes: ['message_created', 'comment_created'],
    failureCount: 0,
    updatedAt: isoMinutesAgo(30),
  });
  map.set(CHANNEL_IDS.vkShop, {
    id: '019a0000-esub-7000-8000-000000000004',
    channelId: CHANNEL_IDS.vkShop,
    workspaceId: PERSONAL_WORKSPACE_ID,
    network: 'vk',
    mode: 'callback',
    status: 'disabled',
    consumers: [],
    eventTypes: [],
    lastError: 'Channel has no_permissions',
    failureCount: 1,
    updatedAt: isoMinutesAgo(1440),
  });
  return map;
}

let state: StoreState = { byChannelId: createSeed() };

export function resetEventSubscriptionStore(): void {
  state = { byChannelId: createSeed() };
}

export function getEventSubscription(
  channelId: string,
): EventSubscriptionResponse | null {
  return state.byChannelId.get(channelId) ?? null;
}

export function repairEventSubscription(
  channelId: string,
): EventSubscriptionResponse | null {
  const existing = state.byChannelId.get(channelId);
  if (!existing) {
    return null;
  }
  const repaired: EventSubscriptionResponse = {
    ...existing,
    status: 'active',
    lastError: undefined,
    failureCount: 0,
    warning:
      existing.network === 'tg'
        ? 'Custom bot webhook was re-registered; ensure no other service uses this bot'
        : undefined,
    updatedAt: new Date().toISOString(),
    lastEventAt: existing.lastEventAt ?? new Date().toISOString(),
  };
  state.byChannelId.set(channelId, repaired);
  return repaired;
}

/** Demo seed channel with failed subscription for e2e repair flow. */
export const DEMO_FAILED_EVENT_CHANNEL_ID = CHANNEL_IDS.vkNews;

/** MSW routes — register with a single spread in handlers.ts. */
export const eventSubscriptionHandlers: HttpHandler[] = [
  http.get('/api/v1/channels/:channelId/event-subscription', ({ request, params }) => {
    const auth = request.headers.get('Authorization');
    if (!auth?.startsWith('Bearer ')) {
      return problem(401, 'unauthorized', 'Missing or invalid access token', 'Unauthorized', '/api/v1');
    }
    if (!can(roleFromAuth(request), 'automations.view')) {
      return problem(
        403,
        INSUFFICIENT_PERMISSIONS_CODE,
        'Forbidden',
        'Forbidden',
        '/api/v1/channels/event-subscription',
      );
    }
    const sub = getEventSubscription(String(params.channelId));
    if (!sub) {
      return problem(
        404,
        'not_found',
        'Event subscription not found',
        'Not found',
        '/api/v1/channels/event-subscription',
      );
    }
    return HttpResponse.json(sub);
  }),
  http.post('/api/v1/channels/:channelId/event-subscription/repair', ({ request, params }) => {
    const auth = request.headers.get('Authorization');
    if (!auth?.startsWith('Bearer ')) {
      return problem(401, 'unauthorized', 'Missing or invalid access token', 'Unauthorized', '/api/v1');
    }
    if (!can(roleFromAuth(request), 'automations.manage')) {
      return problem(
        403,
        INSUFFICIENT_PERMISSIONS_CODE,
        'Forbidden',
        'Forbidden',
        '/api/v1/channels/event-subscription/repair',
      );
    }
    const sub = repairEventSubscription(String(params.channelId));
    if (!sub) {
      return problem(
        404,
        'not_found',
        'Event subscription not found',
        'Not found',
        '/api/v1/channels/event-subscription/repair',
      );
    }
    return HttpResponse.json(sub);
  }),
];
