import { describe, expect, it } from 'vitest';

import { belongsToWorkspace } from './scope';

describe('antispam workspace scope', () => {
  it('keeps rows of the current workspace', () => {
    expect(belongsToWorkspace({ id: '1', workspaceId: 'ws' }, 'ws')).toBe(true);
  });

  it('drops a row that belongs to another workspace', () => {
    expect(belongsToWorkspace({ id: '1', workspaceId: 'other' }, 'ws')).toBe(false);
  });

  it('drops a row without an id', () => {
    expect(belongsToWorkspace({ workspaceId: 'ws' }, 'ws')).toBe(false);
  });

  it('keeps a row when the payload omits workspaceId', () => {
    expect(belongsToWorkspace({ id: '1' }, 'ws')).toBe(true);
  });
});
