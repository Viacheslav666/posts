import type { CrosspostItem } from '@posts/api-client';

import type { ItemStatus } from './types';

export type Crosspost24hCounters = {
  published: number;
  skipped: number;
  failed: number;
  total: number;
};

const SKIPPED: ReadonlySet<ItemStatus> = new Set([
  'filtered_out',
  'skipped_own',
  'skipped_loop',
  'skipped_duplicate',
  'skipped_unsupported_media',
]);

const PUBLISHED: ReadonlySet<ItemStatus> = new Set(['published', 'partially_published']);

function isWithinLast24h(iso: string | undefined | null, nowMs: number): boolean {
  if (!iso) return false;
  const ts = Date.parse(iso);
  if (Number.isNaN(ts)) return false;
  return nowMs - ts <= 24 * 60 * 60 * 1000 && nowMs - ts >= 0;
}

/** Aggregate journal items into 24h counters (client-side; API has no stats endpoint). */
export function aggregate24hCounters(
  items: readonly CrosspostItem[],
  now: Date = new Date(),
): Crosspost24hCounters {
  const nowMs = now.getTime();
  let published = 0;
  let skipped = 0;
  let failed = 0;
  let total = 0;

  for (const item of items) {
    const stamp = item.updatedAt ?? item.createdAt;
    if (!isWithinLast24h(stamp, nowMs)) continue;
    total += 1;
    const status = item.status as ItemStatus | undefined;
    if (!status) continue;
    if (PUBLISHED.has(status)) published += 1;
    else if (SKIPPED.has(status)) skipped += 1;
    else if (status === 'failed') failed += 1;
  }

  return { published, skipped, failed, total };
}
