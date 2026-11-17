import type {
  CompleteUploadRequest,
  CreateUploadRequest,
  CreateUploadResponse,
  MediaAsset,
  MediaPage,
  MessageResponse,
} from '@posts/api-client';

import { getAccessToken } from '@/shared/api-client/session';
import { getApiClient, unwrapApi } from '@/shared/api-client';
import { toApiProblemError } from '@/shared/lib/errors';

import type {
  MediaFolder,
  MediaLibraryItem,
  MediaListFilters,
  MediaQuota,
} from '../model/types';

function authHeaders(): HeadersInit {
  const token = getAccessToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function unwrapFetch<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let body: unknown = null;
    try {
      body = await response.json();
    } catch {
      body = { title: response.statusText };
    }
    throw toApiProblemError(body, response.status);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export async function listMedia(filters: MediaListFilters): Promise<MediaPage & { items?: MediaLibraryItem[] }> {
  const client = getApiClient();
  const result = await client.GET('/api/v1/media', {
    params: {
      query: {
        workspaceId: filters.workspaceId,
        kind: filters.kind,
        status: filters.status,
        cursor: filters.cursor,
        limit: filters.limit,
      },
    },
  });
  // OpenAPI list lacks q/folder/tag — MSW accepts them via raw query when needed.
  if (filters.q || filters.folderId || filters.tag) {
    const params = new URLSearchParams({ workspaceId: filters.workspaceId });
    if (filters.kind) params.set('kind', filters.kind);
    if (filters.status) params.set('status', filters.status);
    if (filters.q) params.set('q', filters.q);
    if (filters.folderId) params.set('folderId', filters.folderId);
    if (filters.tag) params.set('tag', filters.tag);
    if (filters.cursor) params.set('cursor', filters.cursor);
    if (filters.limit) params.set('limit', String(filters.limit));
    return unwrapFetch(await fetch(`/api/v1/media?${params}`, { headers: authHeaders() }));
  }
  return unwrapApi(result) as MediaPage & { items?: MediaLibraryItem[] };
}

export async function getMedia(mediaId: string): Promise<MediaLibraryItem> {
  const client = getApiClient();
  return unwrapApi(await client.GET('/api/v1/media/{mediaId}', {
    params: { path: { mediaId } },
  })) as MediaLibraryItem;
}

export async function createUpload(body: CreateUploadRequest): Promise<CreateUploadResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/media/uploads', {
      body,
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    }),
  );
}

export async function completeUpload(
  mediaId: string,
  body: CompleteUploadRequest,
): Promise<MediaAsset> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/media/{mediaId}/complete', {
      params: { path: { mediaId } },
      body,
    }),
  );
}

/** MSW / BE-8 extension — not yet in frozen OpenAPI paths. */
export async function deleteMedia(mediaId: string): Promise<MessageResponse> {
  return unwrapFetch(
    await fetch(`/api/v1/media/${mediaId}`, {
      method: 'DELETE',
      headers: authHeaders(),
    }),
  );
}

/** MSW / BE-8 extension — POST /media/import-url. */
export async function importMediaUrl(input: {
  workspaceId: string;
  url: string;
  filename?: string;
}): Promise<MediaLibraryItem> {
  return unwrapFetch(
    await fetch('/api/v1/media/import-url', {
      method: 'POST',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    }),
  );
}

export async function getMediaQuota(workspaceId: string): Promise<MediaQuota> {
  const params = new URLSearchParams({ workspaceId });
  return unwrapFetch(await fetch(`/api/v1/media/quota?${params}`, { headers: authHeaders() }));
}

export async function listMediaFolders(workspaceId: string): Promise<MediaFolder[]> {
  const params = new URLSearchParams({ workspaceId });
  const data = await unwrapFetch<{ items: MediaFolder[] }>(
    await fetch(`/api/v1/media/folders?${params}`, { headers: authHeaders() }),
  );
  return data.items ?? [];
}
