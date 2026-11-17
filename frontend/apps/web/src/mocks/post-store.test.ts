import { beforeEach, describe, expect, it } from 'vitest';

import {
  bulkPosts,
  listCalendar,
  listPosts,
  moveCalendarItem,
  POST_IDS,
  resetPostStore,
} from './post-store';
import { PERSONAL_WORKSPACE_ID } from './workspace-store';

describe('post-store MSW', () => {
  beforeEach(() => {
    resetPostStore();
  });

  it('filters posts by status and search', () => {
    const page = listPosts({
      workspaceId: PERSONAL_WORKSPACE_ID,
      status: 'scheduled',
      q: 'дайджест',
    });
    expect(page.items.some((p) => p.id === POST_IDS.morning)).toBe(true);
    expect(page.items.every((p) => p.status === 'scheduled')).toBe(true);
  });

  it('lists calendar items in range and moves with version check', () => {
    const from = new Date(Date.now() - 7 * 86400000).toISOString();
    const to = new Date(Date.now() + 14 * 86400000).toISOString();
    const items = listCalendar({ workspaceId: PERSONAL_WORKSPACE_ID, from, to });
    expect(items.length).toBeGreaterThan(0);

    const moved = moveCalendarItem(POST_IDS.morning, {
      scheduledAt: new Date(Date.now() + 48 * 3600000).toISOString(),
      version: 1,
    });
    expect(moved && !('conflict' in moved)).toBe(true);

    const conflict = moveCalendarItem(POST_IDS.morning, {
      scheduledAt: new Date().toISOString(),
      version: 1,
    });
    expect(conflict && 'conflict' in conflict).toBe(true);
  });

  it('supports bulk pause', () => {
    const result = bulkPosts({
      action: 'pause',
      postIds: [POST_IDS.morning],
    });
    expect(result.affected).toBe(1);
    const page = listPosts({
      workspaceId: PERSONAL_WORKSPACE_ID,
      status: 'paused',
    });
    expect(page.items.some((p) => p.id === POST_IDS.morning)).toBe(true);
  });
});
