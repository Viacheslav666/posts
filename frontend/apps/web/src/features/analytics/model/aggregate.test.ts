import type { PostsAnalyticsMonth } from '@posts/api-client';
import { describe, expect, it } from 'vitest';

import {
  aggregateTopErrors,
  formatAnalyticsRub,
  isAnalyticsYearEmpty,
  publicationIdFromRetryPath,
  sumRevenue,
  toVolumeSeries,
} from './aggregate';

const emptyMonth = (month: number): PostsAnalyticsMonth => ({
  month,
  regularCount: 0,
  adCount: 0,
  repostCount: 0,
  networks: [],
  topErrors: [],
  revenue: { amountWithVat: 0, amountWithoutVat: 0, vatAmount: 0, fee3Pct: 0 },
});

describe('analytics aggregate helpers', () => {
  it('formats RUB amounts in ru-RU', () => {
    expect(formatAnalyticsRub(120)).toMatch(/120/);
    expect(formatAnalyticsRub(3.6)).toMatch(/3[,.]60/);
    expect(formatAnalyticsRub(0)).toContain('₽');
  });

  it('detects empty year', () => {
    expect(
      isAnalyticsYearEmpty({
        workspaceId: 'ws',
        year: 2026,
        timezone: 'Europe/Moscow',
        months: Array.from({ length: 12 }, (_, i) => emptyMonth(i + 1)),
      }),
    ).toBe(true);

    const months = Array.from({ length: 12 }, (_, i) => emptyMonth(i + 1));
    months[10] = { ...months[10]!, regularCount: 3 };
    expect(
      isAnalyticsYearEmpty({
        workspaceId: 'ws',
        year: 2026,
        timezone: 'Europe/Moscow',
        months,
      }),
    ).toBe(false);
  });

  it('builds volume series and revenue totals', () => {
    const months = Array.from({ length: 12 }, (_, i) => emptyMonth(i + 1));
    months[10] = {
      ...months[10]!,
      regularCount: 3,
      adCount: 1,
      repostCount: 1,
      revenue: { amountWithVat: 120, amountWithoutVat: 100, vatAmount: 20, fee3Pct: 3.6 },
    };
    const volume = toVolumeSeries(months);
    expect(volume).toHaveLength(12);
    expect(volume[10]).toMatchObject({ regular: 3, ad: 1, repost: 1, label: 'Ноя' });
    expect(sumRevenue(months)).toEqual({
      amountWithVat: 120,
      amountWithoutVat: 100,
      vatAmount: 20,
      fee3Pct: 3.6,
    });
  });

  it('aggregates top errors across months', () => {
    const months = Array.from({ length: 12 }, (_, i) => emptyMonth(i + 1));
    months[0] = {
      ...months[0]!,
      topErrors: [
        {
          errorCode: 'rate_limited',
          errorMessage: 'VK rate limit',
          count: 2,
          network: 'vk',
          publicationId: '019a0000-0002-7000-8000-000000000010',
          retryPath: '/api/v1/publications/019a0000-0002-7000-8000-000000000010/retry',
        },
      ],
    };
    months[1] = {
      ...months[1]!,
      topErrors: [{ errorCode: 'rate_limited', count: 3, network: 'vk' }],
    };
    const top = aggregateTopErrors(months);
    expect(top[0]).toMatchObject({ errorCode: 'rate_limited', network: 'vk', count: 5 });
    expect(top[0]?.publicationId).toBe('019a0000-0002-7000-8000-000000000010');
  });

  it('parses publication id from retryPath', () => {
    expect(
      publicationIdFromRetryPath(
        '/api/v1/publications/019a0000-0002-7000-8000-000000000099/retry',
      ),
    ).toBe('019a0000-0002-7000-8000-000000000099');
    expect(publicationIdFromRetryPath(undefined)).toBeUndefined();
  });
});
