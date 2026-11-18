import type {
  BatchPostsRequest,
  BatchPostsResponse,
  CreatePostRequest,
  OrdRegisterCreativeResponse,
  Post,
  PreviewRequest,
  PreviewResponse,
  UpdatePostRequest,
} from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

export async function createPost(body: CreatePostRequest): Promise<Post> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/posts', { body }));
}

export async function updatePost(postId: string, body: UpdatePostRequest): Promise<Post> {
  const client = getApiClient();
  return unwrapApi(
    await client.PUT('/api/v1/posts/{postId}', {
      params: { path: { postId } },
      body,
    }),
  );
}

export async function registerPostOrd(postId: string): Promise<OrdRegisterCreativeResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/posts/{postId}/ord/register', {
      params: { path: { postId } },
    }),
  );
}

export async function previewPost(body: PreviewRequest): Promise<PreviewResponse> {
  const client = getApiClient();
  return unwrapApi(await client.POST('/api/v1/posts/preview', { body }));
}

export async function batchCreatePosts(
  body: BatchPostsRequest,
  idempotencyKey?: string,
): Promise<BatchPostsResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/posts/batch', {
      body,
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
    }),
  );
}
