'use client';

import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';

import type { NotificationWsEvent } from '@/features/notifications/model/types';

import { shouldUseStomp } from './stomp-client';

export type NotificationStompOptions = {
  brokerURL?: string;
  accessToken?: string | null;
  onEvent: (event: NotificationWsEvent) => void;
  onConnected?: () => void;
  onDisconnected?: () => void;
  onError?: (error: unknown) => void;
  disabled?: boolean;
};

export type NotificationStompHandle = {
  activate: () => void;
  deactivate: () => Promise<void>;
  isActive: () => boolean;
};

function parseNotificationEvent(message: IMessage): NotificationWsEvent | null {
  try {
    const body = JSON.parse(message.body) as NotificationWsEvent;
    if (!body.notificationId && !body.notification && !body.eventType) {
      return null;
    }
    return body;
  } catch {
    return null;
  }
}

/**
 * Spring STOMP client for user queue `/user/queue/notifications` (BE-11 NotifyWsBridge).
 */
export function createNotificationStompClient(
  options: NotificationStompOptions,
): NotificationStompHandle {
  if (options.disabled || typeof window === 'undefined') {
    return {
      activate: () => undefined,
      deactivate: async () => undefined,
      isActive: () => false,
    };
  }

  let subscription: StompSubscription | null = null;
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  const host = window.location.host;
  const brokerURL = options.brokerURL ?? `${protocol}://${host}/ws`;

  const client = new Client({
    brokerURL,
    reconnectDelay: 3_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    connectHeaders: options.accessToken
      ? { Authorization: `Bearer ${options.accessToken}` }
      : {},
    onConnect: () => {
      options.onConnected?.();
      subscription = client.subscribe('/user/queue/notifications', (message) => {
        const event = parseNotificationEvent(message);
        if (event) options.onEvent(event);
      });
    },
    onDisconnect: () => {
      subscription = null;
      options.onDisconnected?.();
    },
    onStompError: (frame) => {
      options.onError?.(frame);
    },
    onWebSocketError: (event) => {
      options.onError?.(event);
    },
  });

  return {
    activate: () => {
      if (!client.active) client.activate();
    },
    deactivate: async () => {
      subscription?.unsubscribe();
      subscription = null;
      if (client.active) await client.deactivate();
    },
    isActive: () => client.active,
  };
}

export { shouldUseStomp };
