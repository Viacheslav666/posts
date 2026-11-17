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
});
