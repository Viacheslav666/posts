import type { UserProfile } from '@posts/api-client';

/** Fallback when `/me` has no memberships (aligned with MSW demo personal workspace). */
const FALLBACK_WORKSPACE_ID = '019a0000-0002-7000-8000-000000000002';

export function appHomePath(me: UserProfile): string {
  const workspaceId = me.memberships?.[0]?.workspaceId ?? FALLBACK_WORKSPACE_ID;
  return `/app/${workspaceId}`;
}
