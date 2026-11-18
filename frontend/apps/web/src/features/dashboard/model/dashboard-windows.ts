/** Floor a timestamp to the start of its UTC minute (stable React Query windows). */
export function floorToMinute(ms: number): number {
  return Math.floor(ms / 60_000) * 60_000;
}

/** Upcoming publications: now → +7 days, bucketed to the minute. */
export function upcomingWindow(now = Date.now()): { from: string; to: string } {
  const fromMs = floorToMinute(now);
  return {
    from: new Date(fromMs).toISOString(),
    to: new Date(fromMs + 7 * 24 * 60 * 60_000).toISOString(),
  };
}

/** Failed publications: last 24 hours, bucketed to the minute. */
export function failedWindow(now = Date.now()): { from: string; to: string } {
  const toMs = floorToMinute(now);
  return {
    from: new Date(toMs - 24 * 60 * 60_000).toISOString(),
    to: new Date(toMs).toISOString(),
  };
}

/** Stable dashboard list filters for queryKey (no live ISO timestamps). */
export const DASHBOARD_UPCOMING_KEY = {
  dashboard: 'upcoming',
  contentKind: 'post',
} as const;

export const DASHBOARD_FAILED_KEY = {
  dashboard: 'failed',
} as const;

export const DASHBOARD_STORIES_KEY = {
  dashboard: 'stories',
  contentKind: 'story',
} as const;

/** How often dashboard time windows should refresh (ms). */
export const DASHBOARD_WINDOW_REFETCH_MS = 60_000;
