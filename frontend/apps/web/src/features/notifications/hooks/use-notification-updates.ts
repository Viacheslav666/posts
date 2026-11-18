'use client';

import type { Notification, NotificationPage } from '@posts/api-client';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';

import type { NotificationWsEvent } from '@/features/notifications/model/types';
import { queryKeys } from '@/shared/api-client';
import { getAccessToken } from '@/shared/api-client/session';
import {
  createNotificationStompClient,
  shouldUseStomp,
} from '@/shared/ws/notification-stomp-client';
import type { WsConnectionState } from '@/shared/ws/publication-ws-reducer';

import { listNotifications } from '../api/notifications-api';

const POLL_INTERVAL_MS = 12_000;
const STOMP_FAIL_THRESHOLD = 3;

type UseNotificationUpdatesOptions = {
  workspaceId: string;
  enabled?: boolean;
};

function eventToNotification(event: NotificationWsEvent, workspaceId: string): Notification | null {
  if (event.notification?.id) {
    return event.notification;
  }
  if (!event.notificationId && !event.eventType) {
    return null;
  }
  return {
    id: event.notificationId,
    type: event.eventType,
    workspaceId: event.workspaceId ?? workspaceId,
    payload: event.payload,
    createdAt: new Date().toISOString(),
  };
}

function prependNotification(
  page: NotificationPage | undefined,
  notification: Notification,
): NotificationPage {
  const items = page?.items ?? [];
  if (notification.id && items.some((item) => item.id === notification.id)) {
    return page ?? { items, hasMore: false };
  }
  return {
    ...page,
    items: [notification, ...items],
    hasMore: page?.hasMore ?? false,
    nextCursor: page?.nextCursor,
  };
}

/**
 * Live in-app notifications via STOMP `/user/queue/notifications` with polling fallback.
 */
export function useNotificationUpdates({
  workspaceId,
  enabled = true,
}: UseNotificationUpdatesOptions): WsConnectionState {
  const queryClient = useQueryClient();
  const [connection, setConnection] = useState<WsConnectionState>('idle');
  const failCount = useRef(0);

  useEffect(() => {
    if (!enabled || !workspaceId) return;

    const applyEvent = (event: NotificationWsEvent) => {
      const eventWs = event.workspaceId ?? workspaceId;
      if (eventWs !== workspaceId) return;

      const notification = eventToNotification(event, workspaceId);
      if (!notification) {
        void queryClient.invalidateQueries({
          queryKey: queryKeys.notifications.all(workspaceId),
        });
        return;
      }

      queryClient.setQueriesData<NotificationPage>(
        { queryKey: queryKeys.notifications.all(workspaceId) },
        (old) => {
          if (!old || typeof old !== 'object' || !('items' in old)) {
            return prependNotification(undefined, notification);
          }
          return prependNotification(old, notification);
        },
      );

      if (!notification.readAt) {
        queryClient.setQueryData<number>(
          queryKeys.notifications.unreadCount(workspaceId),
          (prev) => (typeof prev === 'number' ? prev + 1 : 1),
        );
      }
    };

    const stomp = createNotificationStompClient({
      accessToken: getAccessToken(),
      disabled: !shouldUseStomp(),
      onEvent: applyEvent,
      onConnected: () => {
        failCount.current = 0;
        setConnection('open');
      },
      onDisconnected: () => {
        setConnection('closed');
      },
      onError: () => {
        failCount.current += 1;
        if (failCount.current >= STOMP_FAIL_THRESHOLD) {
          setConnection('polling');
        }
      },
    });

    if (shouldUseStomp()) {
      setConnection('connecting');
      stomp.activate();
    } else {
      setConnection('polling');
    }

    const poll = async () => {
      try {
        const page = await listNotifications({
          workspaceId,
          unreadOnly: true,
          limit: 20,
        });
        queryClient.setQueryData(queryKeys.notifications.list(workspaceId, { unreadOnly: true }), page);
        queryClient.setQueryData(
          queryKeys.notifications.unreadCount(workspaceId),
          page.items?.filter((n) => !n.readAt).length ?? 0,
        );
        // Soft-refresh full list when panel/page is mounted.
        await queryClient.invalidateQueries({
          queryKey: queryKeys.notifications.list(workspaceId, {}),
          refetchType: 'active',
        });
      } catch {
        // ignore poll errors
      }
    };

    const timer = window.setInterval(() => {
      void poll();
    }, POLL_INTERVAL_MS);

    if (!shouldUseStomp()) {
      void poll();
    }

    return () => {
      window.clearInterval(timer);
      void stomp.deactivate();
    };
  }, [enabled, workspaceId, queryClient]);

  return connection;
}
