/** API authorizes by resource id. The screen still hides a payload from another workspace. */
export function belongsToWorkspace(
  entity: { id?: string | null; workspaceId?: string | null },
  workspaceId: string,
): boolean {
  if (!entity.id) return false;
  if (entity.workspaceId && entity.workspaceId !== workspaceId) return false;
  return true;
}
