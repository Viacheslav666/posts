import type { ChannelPreference, Notification } from '@posts/api-client';

/** Event types from BE-11 PreferenceResolver defaults. */
export const NOTIFICATION_EVENT_TYPES = [
  'publication.failed',
  'publication.succeeded',
  'channel.reauth_required',
  'channel.no_permissions',
  'grabber.moderation_queued',
  'grabber.item_failed',
  'ord.error',
  'ord.erid_failed',
  'invitation.created',
  'team.approval_requested',
] as const;

export type NotificationEventType = (typeof NOTIFICATION_EVENT_TYPES)[number];

export const DELIVERY_CHANNELS = ['email', 'telegram', 'webPush', 'inApp'] as const;

export type DeliveryChannel = (typeof DELIVERY_CHANNELS)[number];

export type NotificationWsEvent = {
  notificationId?: string;
  userId?: string;
  workspaceId?: string;
  eventType?: string;
  dedupeKey?: string;
  payload?: Notification['payload'];
  /** Full notification when API fan-out includes it. */
  notification?: Notification;
};

export const DEFAULT_CHANNEL_PREFERENCE: ChannelPreference = {
  email: true,
  telegram: true,
  webPush: true,
  inApp: true,
};

export function isUnread(notification: Notification): boolean {
  return !notification.readAt;
}
