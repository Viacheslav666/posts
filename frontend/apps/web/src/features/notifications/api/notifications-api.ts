import type {
  MarkNotificationsReadRequest,
  MessageResponse,
  NotificationPage,
  NotificationSettings,
  TelegramLinkStartResponse,
  UpdateNotificationSettingsRequest,
  VapidPublicKeyResponse,
  WebPushSubscribeRequest,
} from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

export type ListNotificationsParams = {
  workspaceId: string;
  unreadOnly?: boolean;
  cursor?: string;
  limit?: number;
};

export async function listNotifications(
  params: ListNotificationsParams,
): Promise<NotificationPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/notifications', {
      params: {
        query: {
          workspaceId: params.workspaceId,
          unreadOnly: params.unreadOnly,
          cursor: params.cursor,
          limit: params.limit,
        },
      },
    }),
  );
}

export async function markNotificationsRead(
  workspaceId: string,
  body: MarkNotificationsReadRequest = {},
): Promise<MessageResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.PUT('/api/v1/notifications/read', {
      params: { query: { workspaceId } },
      body,
    }),
  );
}

export async function getNotificationSettings(
  workspaceId: string,
): Promise<NotificationSettings> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/notification-settings', {
      params: { query: { workspaceId } },
    }),
  );
}

export async function updateNotificationSettings(
  workspaceId: string,
  body: UpdateNotificationSettingsRequest,
): Promise<NotificationSettings> {
  const client = getApiClient();
  return unwrapApi(
    await client.PUT('/api/v1/notification-settings', {
      params: { query: { workspaceId } },
      body,
    }),
  );
}

export async function startTelegramLink(
  workspaceId: string,
): Promise<TelegramLinkStartResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/notification-settings/telegram/link', {
      params: { query: { workspaceId } },
    }),
  );
}

export async function unlinkTelegram(workspaceId: string): Promise<MessageResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.DELETE('/api/v1/notification-settings/telegram', {
      params: { query: { workspaceId } },
    }),
  );
}

export async function subscribeWebPush(
  workspaceId: string,
  body: WebPushSubscribeRequest,
): Promise<MessageResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/notification-settings/web-push', {
      params: { query: { workspaceId } },
      body,
    }),
  );
}

export async function unsubscribeWebPush(
  workspaceId: string,
  endpoint: string,
): Promise<MessageResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.DELETE('/api/v1/notification-settings/web-push', {
      params: { query: { workspaceId, endpoint } },
    }),
  );
}

export async function getVapidPublicKey(): Promise<VapidPublicKeyResponse> {
  const client = getApiClient();
  return unwrapApi(await client.GET('/api/v1/notifications/vapid-public-key'));
}
