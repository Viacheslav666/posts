import { beforeEach, describe, expect, it } from 'vitest';

import { PERSONAL_WORKSPACE_ID } from './workspace-store';
import {
  countUnread,
  getNotificationSettings,
  listNotifications,
  markNotificationsRead,
  pushLiveNotification,
  resetNotificationStoreForTests,
  startTelegramLink,
  updateNotificationSettings,
} from './notification-store';

describe('notification-store (FE-9)', () => {
  beforeEach(() => {
    resetNotificationStoreForTests();
  });

  it('lists seeded notifications with unread filter', () => {
    const all = listNotifications({ workspaceId: PERSONAL_WORKSPACE_ID });
    expect((all.items?.length ?? 0) >= 2).toBe(true);
    const unread = listNotifications({ workspaceId: PERSONAL_WORKSPACE_ID, unreadOnly: true });
    expect(unread.items?.every((n) => !n.readAt)).toBe(true);
    expect(countUnread(PERSONAL_WORKSPACE_ID)).toBe(unread.items?.length);
  });

  it('marks one and all as read', () => {
    const page = listNotifications({ workspaceId: PERSONAL_WORKSPACE_ID, unreadOnly: true });
    const firstId = page.items?.[0]?.id;
    expect(firstId).toBeTruthy();
    markNotificationsRead(PERSONAL_WORKSPACE_ID, { ids: [firstId!] });
    expect(countUnread(PERSONAL_WORKSPACE_ID)).toBe((page.items?.length ?? 1) - 1);
    markNotificationsRead(PERSONAL_WORKSPACE_ID, {});
    expect(countUnread(PERSONAL_WORKSPACE_ID)).toBe(0);
  });

  it('updates preference matrix and telegram link start', () => {
    const updated = updateNotificationSettings(PERSONAL_WORKSPACE_ID, {
      emailEnabled: false,
      preferences: {
        'publication.failed': { email: false, telegram: true, webPush: true, inApp: true },
      },
    });
    expect(updated.emailEnabled).toBe(false);
    expect(updated.preferences?.['publication.failed']?.email).toBe(false);

    const link = startTelegramLink(PERSONAL_WORKSPACE_ID);
    expect(link.deepLink).toContain('t.me/posts_notify_bot');
    const settings = getNotificationSettings(PERSONAL_WORKSPACE_ID);
    expect(settings.telegram?.deepLink).toBeTruthy();
  });

  it('pushLiveNotification prepends unread item', () => {
    const before = countUnread(PERSONAL_WORKSPACE_ID);
    const created = pushLiveNotification({
      type: 'publication.failed',
      workspaceId: PERSONAL_WORKSPACE_ID,
      payload: { title: 'Live' } as never,
    });
    expect(created.id).toBeTruthy();
    expect(countUnread(PERSONAL_WORKSPACE_ID)).toBe(before + 1);
    expect(listNotifications({ workspaceId: PERSONAL_WORKSPACE_ID }).items?.[0]?.id).toBe(
      created.id,
    );
  });
});
