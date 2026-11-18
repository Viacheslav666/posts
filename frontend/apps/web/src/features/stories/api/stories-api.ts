import type {
  BatchStoriesRequest,
  BatchStoriesResponse,
  CreatePostRequest,
  Post,
  StartStoryConnectionRequest,
  StoryConnection,
} from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

export async function createStoryPost(body: CreatePostRequest): Promise<Post> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/posts', {
      body: {
        ...body,
        contentKind: 'story',
      },
    }),
  );
}

export async function batchCreateStories(
  body: BatchStoriesRequest,
  idempotencyKey?: string,
): Promise<BatchStoriesResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/stories/batch', {
      body,
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
    }),
  );
}

export async function getStoryConnection(channelId: string): Promise<StoryConnection> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/channels/{channelId}/story-connection', {
      params: { path: { channelId } },
    }),
  );
}

export async function startStoryConnection(
  channelId: string,
  body: StartStoryConnectionRequest = { confirmReady: true },
): Promise<StoryConnection> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/channels/{channelId}/story-connection', {
      params: { path: { channelId } },
      body,
    }),
  );
}
