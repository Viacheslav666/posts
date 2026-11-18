import type { ChannelPreference, NotificationSettings } from '@posts/api-client';

import { NOTIFICATION_EVENT_TYPES, type NotificationEventType } from './types';

const ALL: ChannelPreference = {
  email: true,
  telegram: true,
  webPush: true,
  inApp: true,
};

const IN_APP_ONLY: ChannelPreference = {
  email: false,
  telegram: false,
  webPush: false,
  inApp: true,
};

const INVITATION: ChannelPreference = {
  email: true,
  telegram: false,
  webPush: false,
  inApp: true,
};

/** Mirrors BE-11 PreferenceResolver.defaultMatrix(). */
export const DEFAULT_PREFERENCE_MATRIX: Record<NotificationEventType, ChannelPreference> = {
  'publication.failed': ALL,
  'publication.succeeded': IN_APP_ONLY,
  'channel.reauth_required': ALL,
  'channel.no_permissions': ALL,
  'grabber.moderation_queued': ALL,
  'ord.error': ALL,
  'ord.erid_failed': ALL,
  'invitation.created': INVITATION,
  'team.approval_requested': ALL,
};

export function buildDefaultNotificationSettings(
  overrides?: Partial<NotificationSettings>,
): NotificationSettings {
  const preferences: Record<string, ChannelPreference> = {};
  for (const type of NOTIFICATION_EVENT_TYPES) {
    preferences[type] = { ...DEFAULT_PREFERENCE_MATRIX[type] };
  }
  return {
    preferences,
    quietHours: {
      enabled: false,
      start: '22:00',
      end: '08:00',
      timezone: 'Europe/Moscow',
    },
    emailEnabled: true,
    telegramBotEnabled: true,
    webPushEnabled: true,
    inAppEnabled: true,
    mutedTypes: [],
    telegram: {
      linked: false,
      botUsername: 'posts_notify_bot',
      deepLink: undefined,
      linkedAt: undefined,
      username: undefined,
    },
    vapidPublicKey: 'BPmockVapidPublicKeyForFe9TestsOnly_abcdefghijklmnopqrstuvwxyz0123456789',
    webPushSubscriptionCount: 0,
    ...overrides,
  };
}
