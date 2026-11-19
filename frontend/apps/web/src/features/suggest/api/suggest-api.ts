import type {
  SuggestBulkJob,
  SuggestBulkRequest,
  SuggestItem,
  SuggestItemApproveRequest,
  SuggestItemPage,
  SuggestItemRejectRequest,
  SuggestSettings,
  SuggestSettingsCopyRequest,
  SuggestSettingsCopyResult,
  SuggestSettingsCreate,
  SuggestSettingsPage,
  SuggestSettingsPatch,
  SuggestSyncResult,
} from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

import type { ItemStatus } from '../model/types';
import { SUGGEST_PAGE_SIZE } from '../model/types';

function idempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export async function listSuggestSettings(params: {
  workspaceId: string;
  projectId: string;
  cursor?: string;
  limit?: number;
}): Promise<SuggestSettingsPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/workspaces/{workspaceId}/projects/{projectId}/suggest/settings', {
      params: {
        path: { workspaceId: params.workspaceId, projectId: params.projectId },
        query: {
          cursor: params.cursor,
          limit: params.limit ?? SUGGEST_PAGE_SIZE,
        },
      },
    }),
  );
}

export async function createSuggestSettings(
  workspaceId: string,
  projectId: string,
  body: SuggestSettingsCreate,
): Promise<SuggestSettings> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/workspaces/{workspaceId}/projects/{projectId}/suggest/settings', {
      params: {
        path: { workspaceId, projectId },
      },
      body,
    }),
  );
}

export async function getSuggestSettings(settingsId: string): Promise<SuggestSettings> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/suggest/settings/{settingsId}', {
      params: { path: { settingsId } },
    }),
  );
}

export async function patchSuggestSettings(
  settingsId: string,
  body: SuggestSettingsPatch,
): Promise<SuggestSettings> {
  const client = getApiClient();
  return unwrapApi(
    await client.PATCH('/api/v1/suggest/settings/{settingsId}', {
      params: { path: { settingsId } },
      body,
    }),
  );
}

export async function activateSuggestSettings(settingsId: string): Promise<SuggestSettings> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/suggest/settings/{settingsId}/activate', {
      params: { path: { settingsId } },
    }),
  );
}

export async function pauseSuggestSettings(settingsId: string): Promise<SuggestSettings> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/suggest/settings/{settingsId}/pause', {
      params: { path: { settingsId } },
    }),
  );
}

export async function resumeSuggestSettings(settingsId: string): Promise<SuggestSettings> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/suggest/settings/{settingsId}/resume', {
      params: { path: { settingsId } },
    }),
  );
}

export async function copySuggestSettings(
  settingsId: string,
  body: SuggestSettingsCopyRequest,
): Promise<SuggestSettingsCopyResult> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/suggest/settings/{settingsId}/copy', {
      params: { path: { settingsId } },
      body,
    }),
  );
}

export async function syncSuggestLast100(settingsId: string): Promise<SuggestSyncResult> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/suggest/settings/{settingsId}/sync-last-100', {
      params: { path: { settingsId } },
    }),
  );
}

export async function listSuggestItems(params: {
  settingsId: string;
  status?: ItemStatus | string;
  authorVkId?: string;
  from?: string;
  to?: string;
  cursor?: string;
  limit?: number;
}): Promise<SuggestItemPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/suggest/settings/{settingsId}/items', {
      params: {
        path: { settingsId: params.settingsId },
        query: {
          status: params.status,
          authorVkId: params.authorVkId,
          from: params.from,
          to: params.to,
          cursor: params.cursor,
          limit: params.limit ?? SUGGEST_PAGE_SIZE,
        },
      },
    }),
  );
}

const MAX_LIST_PAGES = 40;

/** Follow `nextCursor` until the list ends or the page cap is hit. */
export async function listAllSuggestItems(params: {
  settingsId: string;
  status?: ItemStatus | string;
  authorVkId?: string;
  from?: string;
  to?: string;
  limit?: number;
}): Promise<{ items: SuggestItem[]; truncated: boolean }> {
  const items: SuggestItem[] = [];
  let cursor: string | undefined;
  for (let page = 0; page < MAX_LIST_PAGES; page += 1) {
    const result = await listSuggestItems({ ...params, cursor, limit: params.limit ?? 100 });
    items.push(...(result.items ?? []));
    if (!result.hasMore || !result.nextCursor) {
      return { items, truncated: false };
    }
    cursor = result.nextCursor;
  }
  return { items, truncated: true };
}

export async function bulkSuggestItems(
  settingsId: string,
  body: SuggestBulkRequest,
): Promise<SuggestBulkJob> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/suggest/settings/{settingsId}/items/bulk', {
      params: {
        path: { settingsId },
        header: { 'Idempotency-Key': idempotencyKey() },
      },
      body,
    }),
  );
}

export async function getSuggestBulkJob(jobId: string): Promise<SuggestBulkJob> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/suggest/bulk-jobs/{jobId}', {
      params: { path: { jobId } },
    }),
  );
}

export async function getSuggestItem(itemId: string): Promise<SuggestItem> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/suggest/items/{itemId}', {
      params: { path: { itemId } },
    }),
  );
}

export async function approveSuggestItem(
  itemId: string,
  body?: SuggestItemApproveRequest,
): Promise<SuggestItem> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/suggest/items/{itemId}/approve', {
      params: {
        path: { itemId },
        header: { 'Idempotency-Key': idempotencyKey() },
      },
      body: body ?? {},
    }),
  );
}

export async function rejectSuggestItem(
  itemId: string,
  body?: SuggestItemRejectRequest,
): Promise<SuggestItem> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/suggest/items/{itemId}/reject', {
      params: { path: { itemId } },
      body: body ?? {},
    }),
  );
}
