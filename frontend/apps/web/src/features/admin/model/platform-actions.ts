/**
 * Platform-admin actions — mirrors `platform.actions` in
 * docs/product/roles-permissions.yaml (not workspace RBAC).
 * Access is gated by `users.platform_admin` / `UserProfile.platformAdmin`.
 */
export const PLATFORM_ADMIN_ACTIONS = [
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
] as const;

export type PlatformAdminAction = (typeof PLATFORM_ADMIN_ACTIONS)[number];

/** Dangerous mutations that require an explicit confirmation dialog in the admin UI. */
export const DANGEROUS_ADMIN_ACTIONS = [
  'admin.queues.retry',
  'admin.queues.delete',
  'admin.feature_flags.manage',
  'admin.workspaces.adjust_subscription',
  'admin.workspaces.adjust_balance',
] as const;

export function isPlatformAdminAction(value: string): value is PlatformAdminAction {
  return (PLATFORM_ADMIN_ACTIONS as readonly string[]).includes(value);
}
