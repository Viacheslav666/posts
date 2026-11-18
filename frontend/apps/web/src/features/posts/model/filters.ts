export type PostsSort = 'scheduledAt_asc' | 'scheduledAt_desc' | 'title_asc' | 'updatedAt_desc';

export type PostsFiltersState = {
  status?: string;
  channelId?: string;
  projectId?: string;
  network?: string;
  authorId?: string;
  from?: string;
  to?: string;
  q?: string;
  sort: PostsSort;
};

export function defaultPostsFilters(): PostsFiltersState {
  return { sort: 'scheduledAt_desc' };
}

export function postsFiltersToQuery(filters: PostsFiltersState): Record<string, unknown> {
  const query: Record<string, unknown> = { sort: filters.sort };
  if (filters.status) query.status = filters.status;
  if (filters.channelId) query.channelId = filters.channelId;
  if (filters.projectId) query.projectId = filters.projectId;
  if (filters.network) query.network = filters.network;
  if (filters.authorId) query.authorId = filters.authorId;
  if (filters.from) query.from = filters.from;
  if (filters.to) query.to = filters.to;
  if (filters.q?.trim()) query.q = filters.q.trim();
  return query;
}

export const POST_STATUS_OPTIONS = [
  'draft',
  'pending_approval',
  'approved',
  'scheduled',
  'publishing',
  'published',
  'partially_published',
  'failed',
  'paused',
  'archived',
  'deleted_from_network',
] as const;

export function filterScheduledTabDefault(): PostsFiltersState {
  return {
    sort: 'scheduledAt_asc',
    status: 'scheduled',
  };
}
