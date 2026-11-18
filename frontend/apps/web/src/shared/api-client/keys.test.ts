import { describe, expect, it } from 'vitest';

import { queryKeys } from './keys';

describe('queryKeys', () => {
  it('builds hierarchical me and workspace keys', () => {
    expect(queryKeys.me.profile()).toEqual(['posts', 'me', 'profile']);
    expect(queryKeys.workspaces.detail('ws-1')).toEqual(['posts', 'workspaces', 'ws-1']);
    expect(queryKeys.posts.list('ws-1', { status: 'draft' })).toEqual([
      'posts',
      'workspaces',
      'ws-1',
      'posts',
      'list',
      { status: 'draft' },
    ]);
  });

  it('nests channel keys under workspace', () => {
    expect(queryKeys.channels.detail('ws-1', 'ch-9')).toEqual([
      'posts',
      'workspaces',
      'ws-1',
      'channels',
      'ch-9',
    ]);
  });

  it('scopes ORD reports under workspace with stable filters', () => {
    expect(queryKeys.ord.reports('ws-1', { year: 2026, month: 11 })).toEqual([
      'posts',
      'workspaces',
      'ws-1',
      'ord',
      'reports',
      { year: 2026, month: 11 },
    ]);
  });

  it('scopes notification keys under workspace', () => {
    expect(queryKeys.notifications.list('ws-1', { unreadOnly: true })).toEqual([
      'posts',
      'workspaces',
      'ws-1',
      'notifications',
      'list',
      { unreadOnly: true },
    ]);
    expect(queryKeys.notifications.settings('ws-1')).toEqual([
      'posts',
      'workspaces',
      'ws-1',
      'notifications',
      'settings',
    ]);
  });
});
