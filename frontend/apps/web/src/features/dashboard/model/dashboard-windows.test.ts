import { describe, expect, it } from 'vitest';

import {
  DASHBOARD_FAILED_KEY,
  DASHBOARD_UPCOMING_KEY,
  failedWindow,
  floorToMinute,
  upcomingWindow,
} from './dashboard-windows';
import { queryKeys } from '@/shared/api-client';

describe('dashboard time windows', () => {
  it('floors timestamps to the minute', () => {
    expect(floorToMinute(1_700_000_123_456)).toBe(1_700_000_100_000);
  });

  it('returns the same upcoming window within the same minute', () => {
    const base = Date.UTC(2026, 10, 18, 12, 34, 10);
    const a = upcomingWindow(base);
    const b = upcomingWindow(base + 49_000);
    expect(a).toEqual(b);
    expect(a.from).toBe(new Date(floorToMinute(base)).toISOString());
  });

  it('returns the same failed window within the same minute', () => {
    const base = Date.UTC(2026, 10, 18, 12, 34, 55);
    const a = failedWindow(base);
    const b = failedWindow(base + 4_000);
    expect(a).toEqual(b);
  });

  it('keeps dashboard queryKeys free of live ISO timestamps', () => {
    const workspaceId = 'ws-test';
    const upcomingKey = queryKeys.posts.list(workspaceId, { ...DASHBOARD_UPCOMING_KEY });
    const failedKey = queryKeys.posts.list(workspaceId, { ...DASHBOARD_FAILED_KEY });

    const serialized = JSON.stringify([upcomingKey, failedKey]);
    expect(serialized).not.toMatch(/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/);

    // Same filters → identical keys across “renders”
    expect(queryKeys.posts.list(workspaceId, { ...DASHBOARD_UPCOMING_KEY })).toEqual(upcomingKey);
    expect(queryKeys.posts.list(workspaceId, { ...DASHBOARD_FAILED_KEY })).toEqual(failedKey);
  });

  it('scopes upcoming key to posts (not stories)', () => {
    expect(DASHBOARD_UPCOMING_KEY.contentKind).toBe('post');
  });
});
