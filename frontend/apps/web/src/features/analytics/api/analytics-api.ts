import type { PostsAnalyticsResponse } from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

export async function getPostsAnalytics(
  workspaceId: string,
  year: number,
): Promise<PostsAnalyticsResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/analytics/posts', {
      params: { query: { workspaceId, year } },
    }),
  );
}
