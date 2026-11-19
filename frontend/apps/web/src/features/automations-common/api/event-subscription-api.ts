import type { EventSubscriptionResponse } from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

export async function getEventSubscription(
  channelId: string,
): Promise<EventSubscriptionResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/channels/{channelId}/event-subscription', {
      params: { path: { channelId } },
    }),
  );
}

export async function repairEventSubscription(
  channelId: string,
): Promise<EventSubscriptionResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/channels/{channelId}/event-subscription/repair', {
      params: { path: { channelId } },
    }),
  );
}
