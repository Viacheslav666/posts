import type {
  BulkPostsRequest,
  BulkPostsResponse,
  Post,
  PostActionRequest,
  PostPage,
  Publication,
  PublicationAttempt,
  PublicationPage,
} from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

export type ListPostsParams = {
  workspaceId: string;
  from?: string;
  to?: string;
  status?: string;
  channelId?: string;
  projectId?: string;
  network?: string;
  authorId?: string;
  contentKind?: 'post' | 'story';
  q?: string;
  cursor?: string;
  limit?: number;
  sort?: 'scheduledAt_asc' | 'scheduledAt_desc' | 'title_asc' | 'updatedAt_desc';
};

export async function listPosts(params: ListPostsParams): Promise<PostPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/posts', {
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
          authorId: params.authorId,
          contentKind: params.contentKind,
          q: params.q,
          cursor: params.cursor,
          limit: params.limit,
          sort: params.sort,
        },
      },
    }),
  );
}

export async function getPost(postId: string): Promise<Post> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/posts/{postId}', {
      params: { path: { postId } },
    }),
  );
}

export async function deletePost(postId: string): Promise<void> {
  const client = getApiClient();
  const result = await client.DELETE('/api/v1/posts/{postId}', {
    params: { path: { postId } },
  });
  if (!result.response.ok) {
    unwrapApi(result);
  }
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

export async function listPublicationAttempts(
  publicationId: string,
): Promise<PublicationAttempt[]> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/publications/{publicationId}/attempts', {
      params: { path: { publicationId } },
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
