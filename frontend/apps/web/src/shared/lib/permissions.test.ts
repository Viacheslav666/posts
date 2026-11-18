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
  'media.view': ['owner', 'admin', 'editor', 'author', 'viewer'],
  'media.manage': ['owner', 'admin', 'editor', 'author'],
  'posts.view': ['owner', 'admin', 'editor', 'author', 'viewer'],
  'posts.create': ['owner', 'admin', 'editor', 'author'],
  'posts.update': ['owner', 'admin', 'editor', 'author'],
  'posts.delete': ['owner', 'admin', 'editor', 'author'],
  'posts.schedule': ['owner', 'admin', 'editor'],
  'posts.bulk': ['owner', 'admin', 'editor'],
  'posts.retry_publish': ['owner', 'admin', 'editor'],
  'posts.ad.schedule': ['owner', 'admin', 'editor'],
  'calendar.view': ['owner', 'admin', 'editor', 'author', 'viewer'],
  'calendar.move': ['owner', 'admin', 'editor'],
  'ord.view': ['owner', 'admin', 'editor', 'author', 'viewer'],
  'ord.connection.manage': ['owner', 'admin'],
  'ord.contractors.manage': ['owner', 'admin', 'editor'],
  'ord.reports.run': ['owner', 'admin'],
};

describe('permissions matrix (FE-5…FE-12)', () => {
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
    expect(can(null, 'ord.connection.manage')).toBe(false);
    expect(can(null, 'posts.ad.schedule')).toBe(false);
  });
});
