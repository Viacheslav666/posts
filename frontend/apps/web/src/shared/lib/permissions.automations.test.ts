import { describe, expect, it } from 'vitest';

import {
  canManageAutomations,
  canModerateAutomations,
  canViewAutomations,
} from './permissions';

describe('automations permission helpers (FE-21)', () => {
  it('allows view for all roles including viewer', () => {
    expect(canViewAutomations('viewer')).toBe(true);
    expect(canViewAutomations('author')).toBe(true);
    expect(canViewAutomations(null)).toBe(false);
  });

  it('restricts manage/moderate to editor+', () => {
    expect(canManageAutomations('viewer')).toBe(false);
    expect(canManageAutomations('author')).toBe(false);
    expect(canManageAutomations('editor')).toBe(true);
    expect(canManageAutomations('admin')).toBe(true);
    expect(canModerateAutomations('editor')).toBe(true);
    expect(canModerateAutomations('viewer')).toBe(false);
  });
});
