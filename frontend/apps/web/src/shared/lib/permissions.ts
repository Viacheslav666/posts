/**
 * Workspace RBAC matrix for FE-5 (mirrors docs/product/roles-permissions.md).
 */

export const ROLES = ['owner', 'admin', 'editor', 'author', 'viewer'] as const;

export type WorkspaceRole = (typeof ROLES)[number];

export const FE5_ACTIONS = [
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
] as const;

export type PermissionAction = (typeof FE5_ACTIONS)[number];

type RoleActionMatrix = Record<WorkspaceRole, ReadonlySet<PermissionAction>>;

const ALL_VIEW: PermissionAction[] = [
  'workspace.view',
  'members.view',
  'projects.view',
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
];

const OWNER_ONLY: PermissionAction[] = [
  'workspace.delete',
  'billing.view',
  'billing.manage',
];

export const ROLE_ACTIONS: RoleActionMatrix = {
  owner: new Set<PermissionAction>([...ADMIN_TEAM, ...OWNER_ONLY]),
  admin: new Set<PermissionAction>(ADMIN_TEAM),
  editor: new Set<PermissionAction>(ALL_VIEW),
  author: new Set<PermissionAction>(ALL_VIEW),
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
