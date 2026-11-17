import type {
  BulkPostsRequest,
  BulkPostsResponse,
  Post,
  PostActionRequest,
  PostPage,
  Publication,
  PublicationPage,
} from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';
import { getAccessToken } from '@/shared/api-client/session';

export type ListPostsParams = {
  workspaceId: string;
  from?: string;
  to?: string;
  status?: string;
  channelId?: string;
  projectId?: string;
  network?: string;
  /** MSW / proposed OpenAPI extension for author filter. */
  authorId?: string;
  q?: string;
  cursor?: string;
  limit?: number;
  /** MSW extension until OpenAPI adds sort. */
  sort?: string;
};

export async function listPosts(params: ListPostsParams): Promise<PostPage> {
  const client = getApiClient();
  const search = new URLSearchParams();
  search.set('workspaceId', params.workspaceId);
  if (params.from) search.set('from', params.from);
  if (params.to) search.set('to', params.to);
  if (params.status) search.set('status', params.status);
  if (params.channelId) search.set('channelId', params.channelId);
  if (params.projectId) search.set('projectId', params.projectId);
  if (params.network) search.set('network', params.network);
  if (params.authorId) search.set('authorId', params.authorId);
  if (params.q) search.set('q', params.q);
  if (params.cursor) search.set('cursor', params.cursor);
  if (params.limit) search.set('limit', String(params.limit));
  if (params.sort) search.set('sort', params.sort);

  // Use typed client for the contracted query; extra MSW params go via fetch fallback merge.
  const result = await client.GET('/api/v1/posts', {
    params: {
      query: {
        workspaceId: params.workspaceId,
        from: params.from,
        to: params.to,
        status: params.status as
          | 'draft'
          | 'pending_approval'
          | 'approved'
          | 'scheduled'
          | 'publishing'
          | 'published'
          | 'partially_published'
          | 'failed'
          | 'paused'
          | 'archived'
          | 'deleted_from_network'
          | undefined,
        channelId: params.channelId,
        projectId: params.projectId,
        network: params.network as 'vk' | 'tg' | 'ok' | 'max' | 'rutube' | undefined,
        q: params.q,
        cursor: params.cursor,
        limit: params.limit,
      },
    },
  });

  // When authorId/sort requested, hit MSW-aware URL that includes them.
  if (params.authorId || params.sort) {
    const token = getAccessToken();
    const response = await fetch(`/api/v1/posts?${search.toString()}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      credentials: 'include',
    });
    if (!response.ok) {
      unwrapApi({ response, error: await response.json().catch(() => undefined) });
    }
    return (await response.json()) as PostPage;
  }

  return unwrapApi(result);
}

export async function getPost(postId: string): Promise<Post> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/posts/{postId}', {
      params: { path: { postId } },
    }),
  );
}

export async function deletePost(postId: string): Promise<Post | void> {
  const client = getApiClient();
  const result = await client.DELETE('/api/v1/posts/{postId}', {
    params: { path: { postId } },
  });
  if (!result.response.ok) {
    unwrapApi(result);
  }
  return result.data;
}

export async function pausePost(postId: string, body: PostActionRequest): Promise<Post> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/posts/{postId}/pause', {
      params: { path: { postId } },
      body,
    }),
  );
}

export async function resumePost(postId: string, body: PostActionRequest): Promise<Post> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/posts/{postId}/resume', {
      params: { path: { postId } },
      body,
    }),
  );
}

export async function duplicatePost(postId: string): Promise<Post> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/posts/{postId}/duplicate', {
      params: { path: { postId } },
    }),
  );
}

export async function bulkPosts(body: BulkPostsRequest): Promise<BulkPostsResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/posts/bulk', {
      body,
    }),
  );
}

/** Prefer MSW bulk-duplicate; fall back to sequential contracted duplicates. */
export async function bulkDuplicatePosts(postIds: string[]): Promise<BulkPostsResponse> {
  const token = getAccessToken();
  const response = await fetch('/api/v1/posts/bulk-duplicate', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ postIds }),
    credentials: 'include',
  });
  if (response.ok) {
    return (await response.json()) as BulkPostsResponse;
  }

  let affected = 0;
  const failedIds: string[] = [];
  for (const id of postIds) {
    try {
      await duplicatePost(id);
      affected += 1;
    } catch {
      failedIds.push(id);
    }
  }
  return { affected, failedIds };
}

export async function listPostPublications(
  postId: string,
  cursor?: string,
  limit = 50,
): Promise<PublicationPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/posts/{postId}/publications', {
      params: { path: { postId }, query: { cursor, limit } },
    }),
  );
}

export async function retryPublication(publicationId: string): Promise<Publication> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/publications/{publicationId}/retry', {
      params: { path: { publicationId } },
    }),
  );
}
