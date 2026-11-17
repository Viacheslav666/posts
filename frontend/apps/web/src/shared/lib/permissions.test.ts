import { describe, expect, it } from 'vitest';

import {
  can,
  PERMISSION_ACTIONS,
  ROLE_ACTIONS,
  ROLES,
  type PermissionAction,
  type WorkspaceRole,
} from './permissions';

const EXPECTED: Record<PermissionAction, WorkspaceRole[]> = {
  'workspace.view': ['owner', 'admin', 'editor', 'author', 'viewer'],
  'workspace.update': ['owner', 'admin'],
  'workspace.delete': ['owner'],
  'members.view': ['owner', 'admin', 'editor', 'author', 'viewer'],
  'members.invite': ['owner', 'admin'],
  'members.update': ['owner', 'admin'],
  'members.remove': ['owner', 'admin'],
  'projects.manage': ['owner', 'admin'],
  'projects.view': ['owner', 'admin', 'editor', 'author', 'viewer'],
  'audit.view': ['owner', 'admin'],
  'audit.export': ['owner', 'admin'],
  'billing.view': ['owner'],
  'billing.manage': ['owner'],
  'channels.view': ['owner', 'admin', 'editor', 'author', 'viewer'],
  'channels.manage': ['owner', 'admin'],
  'channel_sets.view': ['owner', 'admin', 'editor', 'author', 'viewer'],
  'channel_sets.manage': ['owner', 'admin', 'editor'],
};

describe('permissions matrix (FE-5/FE-6)', () => {
  it('covers all permission actions for every role', () => {
    for (const role of ROLES) {
      for (const action of PERMISSION_ACTIONS) {
        const allowed = EXPECTED[action].includes(role);
        expect(can(role, action), `${role} → ${action}`).toBe(allowed);
        expect(ROLE_ACTIONS[role].has(action)).toBe(allowed);
      }
    }
  });

  it('denies null/undefined roles', () => {
    expect(can(null, 'workspace.view')).toBe(false);
    expect(can(undefined, 'members.invite')).toBe(false);
    expect(can(null, 'channels.manage')).toBe(false);
  });
});
