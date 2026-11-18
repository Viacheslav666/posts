import type {
  MarkNotificationsReadRequest,
  Notification,
  NotificationPage,
  NotificationSettings,
  TelegramLinkStartResponse,
  UpdateNotificationSettingsRequest,
  WebPushSubscribeRequest,
} from '@posts/api-client';

import { buildDefaultNotificationSettings } from '@/features/notifications/model/defaults';

import { DEMO_USER_ID, PERSONAL_WORKSPACE_ID } from './workspace-store';

const MOCK_VAPID =
  'BPmockVapidPublicKeyForFe9TestsOnly_abcdefghijklmnopqrstuvwxyz0123456789';

type NotificationState = {
  items: Notification[];
  settingsByWorkspace: Record<string, NotificationSettings>;
  webPushEndpoints: Set<string>;
  telegramLinkCodes: Map<string, { workspaceId: string; expiresAt: number }>;
  idCounter: number;
};

function nextId(n: number): string {
  return `019a0000-0090-7000-8000-${String(n).padStart(12, '0')}`;
}

function seedNotifications(): Notification[] {
  const now = Date.now();
  return [
    {
      id: nextId(1),
      type: 'publication.failed',
      workspaceId: PERSONAL_WORKSPACE_ID,
      createdAt: new Date(now - 5 * 60_000).toISOString(),
      payload: {
        postId: '019a0000-0003-7000-8000-000000000001',
        title: 'Утренний пост',
        reason: 'rate_limited',
      } as unknown as Notification['payload'],
    },
    {
      id: nextId(2),
      type: 'channel.reauth_required',
      workspaceId: PERSONAL_WORKSPACE_ID,
      createdAt: new Date(now - 60 * 60_000).toISOString(),
      payload: {
        channelId: '019a0000-0004-7000-8000-000000000001',
        network: 'vk',
        title: 'VK · Demo Group',
      } as unknown as Notification['payload'],
    },
    {
      id: nextId(3),
      type: 'grabber.moderation_queued',
      workspaceId: PERSONAL_WORKSPACE_ID,
      createdAt: new Date(now - 2 * 60 * 60_000).toISOString(),
      readAt: new Date(now - 90 * 60_000).toISOString(),
      payload: {
        count: 3,
        ruleName: 'RSS новостей',
      } as unknown as Notification['payload'],
    },
    {
      id: nextId(4),
      type: 'invitation.created',
      workspaceId: PERSONAL_WORKSPACE_ID,
      createdAt: new Date(now - 24 * 60 * 60_000).toISOString(),
      readAt: new Date(now - 20 * 60 * 60_000).toISOString(),
      payload: {
        email: 'colleague@example.com',
        role: 'editor',
      } as unknown as Notification['payload'],
    },
  ];
}

const state: NotificationState = {
  items: seedNotifications(),
  settingsByWorkspace: {
    [PERSONAL_WORKSPACE_ID]: buildDefaultNotificationSettings({
      vapidPublicKey: MOCK_VAPID,
    }),
  },
  webPushEndpoints: new Set(),
  telegramLinkCodes: new Map(),
  idCounter: 10,
};

export function resetNotificationStoreForTests(): void {
  state.items = seedNotifications();
  state.settingsByWorkspace = {
    [PERSONAL_WORKSPACE_ID]: buildDefaultNotificationSettings({
      vapidPublicKey: MOCK_VAPID,
    }),
  };
  state.webPushEndpoints.clear();
  state.telegramLinkCodes.clear();
  state.idCounter = 10;
}

function ensureSettings(workspaceId: string): NotificationSettings {
  if (!state.settingsByWorkspace[workspaceId]) {
    state.settingsByWorkspace[workspaceId] = buildDefaultNotificationSettings({
      vapidPublicKey: MOCK_VAPID,
    });
  }
  return state.settingsByWorkspace[workspaceId]!;
}

export function listNotifications(params: {
  workspaceId: string;
  unreadOnly?: boolean;
  cursor?: string;
  limit?: number;
}): NotificationPage {
  const limit = Math.min(Math.max(params.limit ?? 20, 1), 100);
  let items = state.items
    .filter((n) => n.workspaceId === params.workspaceId)
    .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));

  if (params.unreadOnly) {
    items = items.filter((n) => !n.readAt);
  }

  let start = 0;
  if (params.cursor) {
    const idx = items.findIndex((n) => n.id === params.cursor);
    start = idx >= 0 ? idx + 1 : 0;
  }

  const page = items.slice(start, start + limit);
  const hasMore = start + limit < items.length;
  return {
    items: page,
    hasMore,
    nextCursor: hasMore ? page[page.length - 1]?.id : undefined,
  };
}

export function countUnread(workspaceId: string): number {
  return state.items.filter((n) => n.workspaceId === workspaceId && !n.readAt).length;
}

export function markNotificationsRead(
  workspaceId: string,
  body: MarkNotificationsReadRequest,
): { message: string } {
  const ids = body.ids ?? [];
  const now = new Date().toISOString();
  for (const item of state.items) {
    if (item.workspaceId !== workspaceId || item.readAt) continue;
    if (ids.length === 0 || (item.id && ids.includes(item.id))) {
      item.readAt = now;
    }
  }
  return { message: 'ok' };
}

export function getNotificationSettings(workspaceId: string): NotificationSettings {
  const settings = ensureSettings(workspaceId);
  return {
    ...settings,
    webPushSubscriptionCount: state.webPushEndpoints.size,
    vapidPublicKey: MOCK_VAPID,
  };
}

export function updateNotificationSettings(
  workspaceId: string,
  body: UpdateNotificationSettingsRequest,
): NotificationSettings {
  const current = ensureSettings(workspaceId);
  const next: NotificationSettings = {
    ...current,
    emailEnabled: body.emailEnabled ?? current.emailEnabled,
    telegramBotEnabled: body.telegramBotEnabled ?? current.telegramBotEnabled,
    webPushEnabled: body.webPushEnabled ?? current.webPushEnabled,
    inAppEnabled: body.inAppEnabled ?? current.inAppEnabled,
    mutedTypes: body.mutedTypes ?? current.mutedTypes,
    quietHours: body.quietHours ?? current.quietHours,
    preferences: body.preferences
      ? { ...current.preferences, ...body.preferences }
      : current.preferences,
    webPushSubscriptionCount: state.webPushEndpoints.size,
    vapidPublicKey: MOCK_VAPID,
  };
  state.settingsByWorkspace[workspaceId] = next;
  return next;
}

export function startTelegramLink(workspaceId: string): TelegramLinkStartResponse {
  const code = `link-${workspaceId.slice(0, 8)}-${++state.idCounter}`;
  const expiresAt = Date.now() + 15 * 60_000;
  state.telegramLinkCodes.set(code, { workspaceId, expiresAt });
  const settings = ensureSettings(workspaceId);
  const deepLink = `https://t.me/posts_notify_bot?start=${code}`;
  settings.telegram = {
    ...settings.telegram,
    linked: false,
    botUsername: 'posts_notify_bot',
    deepLink,
  };
  return {
    deepLink,
    botUsername: 'posts_notify_bot',
    codeExpiresAt: new Date(expiresAt).toISOString(),
  };
}

/** Simulate Telegram /start CODE linking for MSW / tests. */
export function completeTelegramLink(code: string, username = 'demo_tg_user'): boolean {
  const entry = state.telegramLinkCodes.get(code);
  if (!entry || entry.expiresAt < Date.now()) {
    return false;
  }
  const settings = ensureSettings(entry.workspaceId);
  settings.telegram = {
    linked: true,
    botUsername: 'posts_notify_bot',
    linkedAt: new Date().toISOString(),
    username,
    deepLink: undefined,
  };
  state.telegramLinkCodes.delete(code);
  return true;
}

export function unlinkTelegram(workspaceId: string): { message: string } {
  const settings = ensureSettings(workspaceId);
  settings.telegram = {
    linked: false,
    botUsername: 'posts_notify_bot',
    linkedAt: undefined,
    username: undefined,
    deepLink: undefined,
  };
  return { message: 'ok' };
}

export function subscribeWebPush(
  workspaceId: string,
  body: WebPushSubscribeRequest,
): { message: string } {
  state.webPushEndpoints.add(body.endpoint);
  const settings = ensureSettings(workspaceId);
  settings.webPushSubscriptionCount = state.webPushEndpoints.size;
  settings.webPushEnabled = true;
  return { message: 'ok' };
}

export function unsubscribeWebPush(
  workspaceId: string,
  endpoint: string,
): { message: string } {
  state.webPushEndpoints.delete(endpoint);
  const settings = ensureSettings(workspaceId);
  settings.webPushSubscriptionCount = state.webPushEndpoints.size;
  return { message: 'ok' };
}

export function getVapidPublicKey(): { vapidPublicKey: string } {
  return { vapidPublicKey: MOCK_VAPID };
}

/** Inject a live in-app notification (simulates WS fan-out for tests). */
export function pushLiveNotification(
  partial: Partial<Notification> & { type: string; workspaceId: string },
): Notification {
  const notification: Notification = {
    id: partial.id ?? nextId(++state.idCounter),
    type: partial.type,
    workspaceId: partial.workspaceId,
    createdAt: partial.createdAt ?? new Date().toISOString(),
    readAt: partial.readAt,
    payload: partial.payload,
  };
  state.items.unshift(notification);
  return notification;
}

export function getDemoUserId(): string {
  return DEMO_USER_ID;
}
