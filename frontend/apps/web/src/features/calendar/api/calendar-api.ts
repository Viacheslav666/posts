import type { CalendarItem, MoveCalendarItemRequest, Post } from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

export type ListCalendarParams = {
  workspaceId: string;
  from: string;
  to: string;
  projectId?: string;
  channelId?: string;
  authorId?: string;
  network?: string;
  status?: string;
  timezone?: string;
};

export async function listCalendar(params: ListCalendarParams): Promise<CalendarItem[]> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/calendar', {
      params: {
        query: {
          workspaceId: params.workspaceId,
          from: params.from,
          to: params.to,
          projectId: params.projectId,
          channelId: params.channelId,
          authorId: params.authorId,
          network: params.network as ListCalendarParams['network'] &
            ('vk' | 'tg' | 'ok' | 'max' | 'rutube' | undefined),
          status: params.status as ListCalendarParams['status'] &
            (
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
              | undefined
            ),
          timezone: params.timezone,
        },
      },
    }),
  );
}

export async function moveCalendarItem(
  postId: string,
  body: MoveCalendarItemRequest,
): Promise<Post> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/calendar/{postId}/move', {
      params: { path: { postId } },
      body,
    }),
  );
}
