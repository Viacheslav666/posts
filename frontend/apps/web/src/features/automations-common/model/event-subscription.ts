import type { EventSubscriptionResponse } from '@posts/api-client';

/** Backend statuses from BE-32 `channel_event_subscriptions.status`. */
export type EventSubscriptionStatus =
  | 'active'
  | 'pending'
  | 'failed'
  | 'degraded'
  | 'disabled'
  | 'unknown';

export type EventSubscriptionUiStatus = 'connected' | 'pending' | 'failed' | 'disabled' | 'unknown';

const KNOWN: EventSubscriptionStatus[] = [
  'active',
  'pending',
  'failed',
  'degraded',
  'disabled',
];

export function normalizeEventSubscriptionStatus(
  status: string | undefined | null,
): EventSubscriptionStatus {
  if (status && (KNOWN as string[]).includes(status)) {
    return status as EventSubscriptionStatus;
  }
  return 'unknown';
}

export function toEventSubscriptionUiStatus(
  status: string | undefined | null,
): EventSubscriptionUiStatus {
  switch (normalizeEventSubscriptionStatus(status)) {
    case 'active':
      return 'connected';
    case 'pending':
      return 'pending';
    case 'failed':
    case 'degraded':
      return 'failed';
    case 'disabled':
      return 'disabled';
    default:
      return 'unknown';
  }
}

export type NetworkHintKey = 'vk' | 'tg' | 'max' | 'other';

export function networkHintKey(network: string | undefined | null): NetworkHintKey {
  if (network === 'vk' || network === 'tg' || network === 'max') {
    return network;
  }
  return 'other';
}

export function isRepairableStatus(status: string | undefined | null): boolean {
  const ui = toEventSubscriptionUiStatus(status);
  return ui === 'failed' || ui === 'pending' || ui === 'disabled';
}

export type EventSubscriptionView = EventSubscriptionResponse & {
  uiStatus: EventSubscriptionUiStatus;
};
