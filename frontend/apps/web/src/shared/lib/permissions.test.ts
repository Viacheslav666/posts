import { describe, expect, it } from 'vitest';

import { can, FE5_ACTIONS, ROLE_ACTIONS, ROLES, type PermissionAction, type WorkspaceRole } from './permissions';

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
};

describe('permissions matrix (FE-5)', () => {
  it('covers all FE-5 actions for every role', () => {
    for (const role of ROLES) {
      for (const action of FE5_ACTIONS) {
        const allowed = EXPECTED[action].includes(role);
        expect(can(role, action), `${role} → ${action}`).toBe(allowed);
        expect(ROLE_ACTIONS[role].has(action)).toBe(allowed);
      }
    }
  });

  it('denies null/undefined roles', () => {
    expect(can(null, 'workspace.view')).toBe(false);
    expect(can(undefined, 'members.invite')).toBe(false);
  });
});
