import { describe, expect, it } from 'vitest';

import { aggregate24hCounters } from './stats';

describe('aggregate24hCounters', () => {
  const now = new Date('2026-11-19T12:00:00.000Z');

  it('counts published, skipped and failed within 24h', () => {
    const counters = aggregate24hCounters(
      [
        {
          id: '1',
          status: 'published',
          createdAt: '2026-11-19T10:00:00.000Z',
        },
        {
          id: '2',
          status: 'partially_published',
          createdAt: '2026-11-19T11:00:00.000Z',
        },
        {
          id: '3',
          status: 'filtered_out',
          createdAt: '2026-11-19T09:00:00.000Z',
        },
        {
          id: '4',
          status: 'skipped_loop',
          createdAt: '2026-11-19T08:00:00.000Z',
        },
        {
          id: '5',
          status: 'failed',
          createdAt: '2026-11-19T07:00:00.000Z',
        },
        {
          id: '6',
          status: 'published',
          createdAt: '2026-11-17T12:00:00.000Z',
        },
      ],
      now,
    );
    expect(counters).toEqual({
      published: 2,
      skipped: 2,
      failed: 1,
      total: 5,
    });
  });

  it('returns zeros for empty list', () => {
    expect(aggregate24hCounters([], now)).toEqual({
      published: 0,
      skipped: 0,
      failed: 0,
      total: 0,
    });
  });
});
