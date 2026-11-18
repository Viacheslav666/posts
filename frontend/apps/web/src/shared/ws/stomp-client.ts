'use client';

import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';

import type { PublicationUpdatedEvent } from './publication-ws-reducer';

export type StompClientOptions = {
  /** Absolute or relative ws URL; default `/ws`. */
  brokerURL?: string;
  accessToken?: string | null;
  workspaceId: string;
  onEvent: (event: PublicationUpdatedEvent) => void;
  onConnected?: () => void;
  onDisconnected?: () => void;
  onError?: (error: unknown) => void;
  /** Disable real WebSocket (MSW / tests) — caller should poll. */
  disabled?: boolean;
};

export type StompHandle = {
  activate: () => void;
  deactivate: () => Promise<void>;
  isActive: () => boolean;
};

function parsePublicationEvent(message: IMessage): PublicationUpdatedEvent | null {
  try {
    const body = JSON.parse(message.body) as Partial<PublicationUpdatedEvent> & {
      type?: string;
      publication?: PublicationUpdatedEvent['publication'];
    };
    if (!body.publication) return null;
    return {
      type: 'publication.updated',
      publication: body.publication,
      postId: body.postId ?? body.publication.postId,
      postStatus: body.postStatus,
    };
  } catch {
    return null;
  }
}

/**
 * Spring STOMP client for `/topic/workspaces.{id}.publications`.
 * Reconnect is handled by @stomp/stompjs; caller falls back to polling on repeated failure.
 */
export function createPublicationStompClient(options: StompClientOptions): StompHandle {
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
      subscription = client.subscribe(
        `/topic/workspaces.${options.workspaceId}.publications`,
        (message) => {
          const event = parsePublicationEvent(message);
          if (event) options.onEvent(event);
        },
      );
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

/** Prefer polling in MSW / local mock mode — STOMP broker is not available. */
export function shouldUseStomp(): boolean {
  if (typeof window === 'undefined') return false;
  if (process.env.NEXT_PUBLIC_API_MOCKING === 'true') return false;
  if (process.env.NEXT_PUBLIC_WS_DISABLED === 'true') return false;
  return true;
}
