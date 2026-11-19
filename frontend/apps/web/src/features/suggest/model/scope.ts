import type { SuggestBulkJob, SuggestItem } from '@posts/api-client';

const SETTLED_BULK_STATUSES = new Set(['succeeded', 'failed', 'completed', 'cancelled']);

/**
 * Drop items the list endpoint must not surface for this workspace/settings pair.
 * The API authorizes by resource id; the screen still refuses a cross-workspace payload.
 */
export function suggestItemInScope(
  item: Pick<SuggestItem, 'id' | 'workspaceId' | 'settingsId' | 'channelId'>,
  scope: { workspaceId: string; settingsId: string; channelId?: string },
): boolean {
  if (!item.id) return false;
  if (item.workspaceId && item.workspaceId !== scope.workspaceId) return false;
  if (item.settingsId && item.settingsId !== scope.settingsId) return false;
  if (scope.channelId && item.channelId && item.channelId !== scope.channelId) return false;
  return true;
}

export function suggestJobInScope(
  job: Pick<SuggestBulkJob, 'workspaceId' | 'settingsId'>,
  scope: { workspaceId: string; settingsId: string },
): boolean {
  if (job.workspaceId && job.workspaceId !== scope.workspaceId) return false;
  if (job.settingsId && job.settingsId !== scope.settingsId) return false;
  return true;
}

/** Backend terminal statuses are `succeeded` / `failed`. `completed` is accepted from older mocks. */
export function isBulkJobSettled(job: Pick<SuggestBulkJob, 'status' | 'finishedAt' | 'progressPercent'>): boolean {
  if (job.finishedAt) return true;
  const status = job.status ?? '';
  if (SETTLED_BULK_STATUSES.has(status)) return true;
  if ((job.progressPercent ?? 0) >= 100 && status !== 'running' && status !== 'pending') {
    return true;
  }
  return false;
}
