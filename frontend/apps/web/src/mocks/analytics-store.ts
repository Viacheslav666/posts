import type { PostsAnalyticsMonth, PostsAnalyticsResponse } from '@posts/api-client';

import { POST_IDS, PUBLICATION_IDS } from './post-store';

const EMPTY_REVENUE = {
  amountWithVat: 0,
  amountWithoutVat: 0,
  vatAmount: 0,
  fee3Pct: 0,
};

function emptyMonth(month: number): PostsAnalyticsMonth {
  return {
    month,
    regularCount: 0,
    adCount: 0,
    repostCount: 0,
    networks: [],
    topErrors: [],
    revenue: { ...EMPTY_REVENUE },
  };
}

/** Demo year payload for FE-19 MSW (mirrors BE-30 contract examples). */
export function buildPostsAnalytics(
  workspaceId: string,
  year: number,
): PostsAnalyticsResponse {
  const months = Array.from({ length: 12 }, (_, index) => emptyMonth(index + 1));

  if (year === new Date().getFullYear() || year === 2026) {
    months[10] = {
      month: 11,
      regularCount: 12,
      adCount: 3,
      repostCount: 2,
      networks: [
        { network: 'vk', succeeded: 8, failed: 2 },
        { network: 'tg', succeeded: 4, failed: 1 },
        { network: 'ok', succeeded: 2, failed: 0 },
        { network: 'max', succeeded: 1, failed: 1 },
      ],
      topErrors: [
        {
          errorCode: 'rate_limited',
          errorMessage: 'VK rate limit',
          count: 3,
          network: 'vk',
          postId: POST_IDS.failed,
          publicationId: PUBLICATION_IDS.failedVk,
          retryPath: `/api/v1/publications/${PUBLICATION_IDS.failedVk}/retry`,
          runAt: '2026-11-15T09:00:00Z',
        },
        {
          errorCode: 'token_expired',
          errorMessage: 'Telegram bot token expired',
          count: 1,
          network: 'tg',
          postId: POST_IDS.failed,
        },
      ],
      revenue: {
        amountWithVat: 120,
        amountWithoutVat: 100,
        vatAmount: 20,
        fee3Pct: 3.6,
      },
      computedAt: '2026-11-18T12:00:00Z',
    };
    months[9] = {
      month: 10,
      regularCount: 5,
      adCount: 1,
      repostCount: 0,
      networks: [
        { network: 'vk', succeeded: 4, failed: 0 },
        { network: 'tg', succeeded: 2, failed: 0 },
      ],
      topErrors: [],
      revenue: {
        amountWithVat: 60,
        amountWithoutVat: 50,
        vatAmount: 10,
        fee3Pct: 1.8,
      },
    };
  }

  return {
    workspaceId,
    year,
    timezone: 'Europe/Moscow',
    months,
    computedAt: new Date().toISOString(),
  };
}
