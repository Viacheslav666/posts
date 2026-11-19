import { describe, expect, it } from 'vitest';

import { isBulkJobSettled, suggestItemInScope, suggestJobInScope } from './scope';

const scope = {
  workspaceId: 'ws-1',
  settingsId: 'set-1',
  channelId: 'ch-1',
};

describe('suggest scope', () => {
  it('keeps items that belong to the open settings', () => {
    expect(
      suggestItemInScope(
        { id: 'i1', workspaceId: 'ws-1', settingsId: 'set-1', channelId: 'ch-1' },
        scope,
      ),
    ).toBe(true);
  });

  it('rejects a cross-workspace or cross-settings item', () => {
    expect(
      suggestItemInScope(
        { id: 'i1', workspaceId: 'ws-2', settingsId: 'set-1', channelId: 'ch-1' },
        scope,
      ),
    ).toBe(false);
    expect(
      suggestItemInScope(
        { id: 'i1', workspaceId: 'ws-1', settingsId: 'set-2', channelId: 'ch-1' },
        scope,
      ),
    ).toBe(false);
  });

  it('treats succeeded and failed bulk jobs as settled, not the word completed', () => {
    expect(isBulkJobSettled({ status: 'succeeded', progressPercent: 100 })).toBe(true);
    expect(isBulkJobSettled({ status: 'failed', progressPercent: 40 })).toBe(true);
    expect(isBulkJobSettled({ status: 'running', progressPercent: 40 })).toBe(false);
    expect(isBulkJobSettled({ status: 'running', progressPercent: 100 })).toBe(false);
    expect(isBulkJobSettled({ status: 'pending', finishedAt: '2026-11-19T00:00:00Z' })).toBe(true);
  });

  it('rejects a bulk job from another workspace', () => {
    expect(suggestJobInScope({ workspaceId: 'ws-2', settingsId: 'set-1' }, scope)).toBe(false);
    expect(suggestJobInScope({ workspaceId: 'ws-1', settingsId: 'set-1' }, scope)).toBe(true);
  });
});
