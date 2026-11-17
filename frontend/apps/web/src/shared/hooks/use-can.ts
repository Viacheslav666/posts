'use client';

import { useParams } from 'next/navigation';

import { useMe } from '@/shared/hooks/use-me';
import {
  can,
  isWorkspaceRole,
  type PermissionAction,
  type WorkspaceRole,
} from '@/shared/lib/permissions';

function resolveWorkspaceId(explicit?: string, param?: string | string[]): string | undefined {
  if (explicit) {
    return explicit;
  }
  if (typeof param === 'string') {
    return param;
  }
  if (Array.isArray(param) && param[0]) {
    return param[0];
  }
  return undefined;
}

/**
 * Whether the current user may perform `action` in the given (or route) workspace.
 * Role comes from `GET /me` memberships for that workspace.
 */
export function useCan(action: PermissionAction, workspaceId?: string): boolean {
  const params = useParams<{ workspace?: string }>();
  const { data } = useMe();
  const id = resolveWorkspaceId(workspaceId, params.workspace);
  if (!id || !data?.memberships) {
    return false;
  }
  const membership = data.memberships.find((item) => item.workspaceId === id);
  const role: WorkspaceRole | undefined = isWorkspaceRole(membership?.role)
    ? membership.role
    : undefined;
  return can(role, action);
}

export function useWorkspaceRole(workspaceId?: string): WorkspaceRole | null {
  const params = useParams<{ workspace?: string }>();
  const { data } = useMe();
  const id = resolveWorkspaceId(workspaceId, params.workspace);
  if (!id || !data?.memberships) {
    return null;
  }
  const membership = data.memberships.find((item) => item.workspaceId === id);
  return isWorkspaceRole(membership?.role) ? membership.role : null;
}
