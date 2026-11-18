/**
 * Workspace RBAC matrix (mirrors docs/product/roles-permissions.yaml).
 * FE-5…FE-8 + FE-12 ORD / ad marking + FE-14 grabber/automations actions.
 */

export const ROLES = ['owner', 'admin', 'editor', 'author', 'viewer'] as const;

export type WorkspaceRole = (typeof ROLES)[number];

export const PERMISSION_ACTIONS = [
  'workspace.view',
  'workspace.update',
  'workspace.delete',
  'members.view',
  'members.invite',
  'members.update',
  'members.remove',
  'projects.manage',
  'projects.view',
  'audit.view',
  'audit.export',
  'billing.view',
  'billing.manage',
  'channels.view',
  'channels.manage',
  'channel_sets.view',
  'channel_sets.manage',
  'media.view',
  'media.manage',
  'posts.view',
  'posts.create',
  'posts.update',
  'posts.delete',
  'posts.schedule',
  'posts.bulk',
  'posts.retry_publish',
  'posts.ad.schedule',
  'calendar.view',
  'calendar.move',
  'ord.view',
  'ord.connection.manage',
  'ord.contractors.manage',
  'ord.reports.run',
  'automations.view',
  'automations.manage',
  'automations.moderate',
] as const;

/** @deprecated Prefer PERMISSION_ACTIONS — kept for existing FE-5 imports. */
export const FE5_ACTIONS = PERMISSION_ACTIONS;

export type PermissionAction = (typeof PERMISSION_ACTIONS)[number];

type RoleActionMatrix = Record<WorkspaceRole, ReadonlySet<PermissionAction>>;

const ALL_VIEW: PermissionAction[] = [
  'workspace.view',
  'members.view',
  'projects.view',
  'channels.view',
  'channel_sets.view',
  'media.view',
  'posts.view',
  'calendar.view',
  'ord.view',
  'automations.view',
];

const ADMIN_TEAM: PermissionAction[] = [
  ...ALL_VIEW,
  'workspace.update',
  'members.invite',
  'members.update',
  'members.remove',
  'projects.manage',
  'audit.view',
  'audit.export',
  'channels.manage',
  'channel_sets.manage',
  'media.manage',
  'posts.create',
  'posts.update',
  'posts.delete',
  'posts.schedule',
  'posts.bulk',
  'posts.retry_publish',
  'posts.ad.schedule',
  'calendar.move',
  'ord.connection.manage',
  'ord.contractors.manage',
  'ord.reports.run',
  'automations.manage',
  'automations.moderate',
];

const OWNER_ONLY: PermissionAction[] = [
  'workspace.delete',
  'billing.view',
  'billing.manage',
];

const EDITOR_EXTRA: PermissionAction[] = [
  'channel_sets.manage',
  'media.manage',
  'posts.create',
  'posts.update',
  'posts.delete',
  'posts.schedule',
  'posts.bulk',
  'posts.retry_publish',
  'posts.ad.schedule',
  'calendar.move',
  'ord.contractors.manage',
  'automations.manage',
  'automations.moderate',
];

const AUTHOR_EXTRA: PermissionAction[] = [
  'media.manage',
  'posts.create',
  'posts.update',
  'posts.delete',
];

export const ROLE_ACTIONS: RoleActionMatrix = {
  owner: new Set<PermissionAction>([...ADMIN_TEAM, ...OWNER_ONLY]),
  admin: new Set<PermissionAction>(ADMIN_TEAM),
  editor: new Set<PermissionAction>([...ALL_VIEW, ...EDITOR_EXTRA]),
  author: new Set<PermissionAction>([...ALL_VIEW, ...AUTHOR_EXTRA]),
  viewer: new Set<PermissionAction>(ALL_VIEW),
};

export function can(role: WorkspaceRole | null | undefined, action: PermissionAction): boolean {
  if (!role) {
    return false;
  }
  return ROLE_ACTIONS[role]?.has(action) ?? false;
}

export function isWorkspaceRole(value: unknown): value is WorkspaceRole {
  return typeof value === 'string' && (ROLES as readonly string[]).includes(value);
}
