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

import { getApiBaseUrl, getAccessToken, getApiClient, unwrapApi } from '@/shared/api-client';
import { toApiProblemError } from '@/shared/lib/errors';

import type { ItemStatus } from '../model/types';

function idempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/**
 * Raw JSON fetch for endpoints whose OpenAPI 200 body typing is incomplete /
 * historically missing. Prefer {@link getApiClient}+{@link unwrapApi} when schemas exist.
 */
async function fetchSuggestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');
  if (init?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  const token = getAccessToken();
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${getApiBaseUrl()}${path}`, {
    ...init,
    headers,
    credentials: 'include',
  });

  if (!response.ok) {
    let problem: unknown = { title: response.statusText };
    try {
      problem = await response.json();
    } catch {
      // ignore non-JSON
    }
    throw toApiProblemError(problem, response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
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
          limit: params.limit ?? 50,
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
  // OpenAPI historically omitted 200 schema — use raw fetch with typed cast.
  return fetchSuggestJson<SuggestSettings>(`/api/v1/suggest/settings/${settingsId}/activate`, {
    method: 'POST',
  });
}

export async function pauseSuggestSettings(settingsId: string): Promise<SuggestSettings> {
  return fetchSuggestJson<SuggestSettings>(`/api/v1/suggest/settings/${settingsId}/pause`, {
    method: 'POST',
  });
}

export async function resumeSuggestSettings(settingsId: string): Promise<SuggestSettings> {
  return fetchSuggestJson<SuggestSettings>(`/api/v1/suggest/settings/${settingsId}/resume`, {
    method: 'POST',
  });
}

export async function copySuggestSettings(
  settingsId: string,
  body: SuggestSettingsCopyRequest,
): Promise<SuggestSettingsCopyResult> {
  return fetchSuggestJson<SuggestSettingsCopyResult>(
    `/api/v1/suggest/settings/${settingsId}/copy`,
    {
      method: 'POST',
      body: JSON.stringify(body),
    },
  );
}

export async function syncSuggestLast100(settingsId: string): Promise<SuggestSyncResult> {
  return fetchSuggestJson<SuggestSyncResult>(
    `/api/v1/suggest/settings/${settingsId}/sync-last-100`,
    { method: 'POST' },
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
          limit: params.limit ?? 50,
        },
      },
    }),
  );
}

export async function bulkSuggestItems(
  settingsId: string,
  body: SuggestBulkRequest,
): Promise<SuggestBulkJob> {
  return fetchSuggestJson<SuggestBulkJob>(
    `/api/v1/suggest/settings/${settingsId}/items/bulk`,
    {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey() },
      body: JSON.stringify(body),
    },
  );
}

export async function getSuggestBulkJob(jobId: string): Promise<SuggestBulkJob> {
  return fetchSuggestJson<SuggestBulkJob>(`/api/v1/suggest/bulk-jobs/${jobId}`);
}

export async function getSuggestItem(itemId: string): Promise<SuggestItem> {
  return fetchSuggestJson<SuggestItem>(`/api/v1/suggest/items/${itemId}`);
}

export async function approveSuggestItem(
  itemId: string,
  body?: SuggestItemApproveRequest,
): Promise<SuggestItem> {
  return fetchSuggestJson<SuggestItem>(`/api/v1/suggest/items/${itemId}/approve`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey() },
    body: JSON.stringify(body ?? {}),
  });
}

export async function rejectSuggestItem(
  itemId: string,
  body?: SuggestItemRejectRequest,
): Promise<SuggestItem> {
  return fetchSuggestJson<SuggestItem>(`/api/v1/suggest/items/${itemId}/reject`, {
    method: 'POST',
    body: JSON.stringify(body ?? {}),
  });
}
