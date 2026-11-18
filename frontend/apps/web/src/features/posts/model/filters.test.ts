import { describe, expect, it } from 'vitest';

import {
  defaultPostsFilters,
  filterScheduledTabDefault,
  postsFiltersToQuery,
} from './filters';

describe('posts filters', () => {
  it('serializes active filters for query keys', () => {
    const query = postsFiltersToQuery({
      ...defaultPostsFilters(),
      status: 'scheduled',
      network: 'vk',
      q: '  digests  ',
      authorId: 'u1',
    });
    expect(query).toMatchObject({
      status: 'scheduled',
      network: 'vk',
      q: 'digests',
      authorId: 'u1',
      sort: 'scheduledAt_desc',
    });
  });

  it('defaults scheduled tab to ascending schedule', () => {
    expect(filterScheduledTabDefault().sort).toBe('scheduledAt_asc');
    expect(filterScheduledTabDefault().status).toBe('scheduled');
  });
});
