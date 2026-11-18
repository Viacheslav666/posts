import { describe, expect, it } from 'vitest';

import { isPlatformAdminAction, PLATFORM_ADMIN_ACTIONS } from './platform-actions';

describe('platform admin RBAC catalog', () => {
  it('matches roles-permissions.yaml platform.actions', () => {
    expect(PLATFORM_ADMIN_ACTIONS).toEqual([
      'admin.users.search',
      'admin.users.get',
      'admin.users.impersonate',
      'admin.workspaces.search',
      'admin.workspaces.get',
      'admin.workspaces.adjust_subscription',
      'admin.workspaces.adjust_balance',
      'admin.plans.manage',
      'admin.promos.manage',
      'admin.queues.view',
      'admin.queues.retry',
      'admin.queues.delete',
      'admin.feature_flags.manage',
      'admin.announcements.manage',
      'admin.audit.view',
    ]);
  });

  it('type-guards known actions', () => {
    expect(isPlatformAdminAction('admin.users.impersonate')).toBe(true);
    expect(isPlatformAdminAction('billing.view')).toBe(false);
  });
});
