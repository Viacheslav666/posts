import type { TargetMode } from './types';
import { isPostponedTarget } from './types';

/** Mirrors {@code GrabberModeMatrix} so the form can render before the API responds. */
export type GrabberModeCaps = {
  actionButton: boolean;
  firstComment: boolean;
  commentAttachments: boolean;
  scheduledDelete: boolean;
  interval: boolean;
};

export type GrabberModeMatrix = {
  modes: Array<GrabberModeCaps & { id: string; postponed: boolean; repost: boolean; available?: boolean }>;
  intervalDisables: string[];
  postponedHorizonDays: number;
  maxIntervalMinutes: number;
  maxSources: number;
  defaultMaxAgeDays: number;
};

const FALLBACK_INTERVAL_DISABLES = ['commentAttachments'];

export function capabilitiesFor(
  mode: string,
  intervalMinutes: number,
  matrix?: GrabberModeMatrix,
): GrabberModeCaps {
  const row = matrix?.modes.find((item) => item.id === mode);
  const postponed = row ? row.postponed : isPostponedTarget(mode as TargetMode);
  const caps: GrabberModeCaps = row
    ? {
        actionButton: row.actionButton,
        firstComment: row.firstComment,
        commentAttachments: row.commentAttachments,
        scheduledDelete: row.scheduledDelete,
        interval: row.interval,
      }
    : {
        actionButton: !postponed,
        firstComment: !postponed,
        commentAttachments: !postponed,
        scheduledDelete: !postponed,
        interval: !postponed,
      };
  const disables = matrix?.intervalDisables ?? FALLBACK_INTERVAL_DISABLES;
  if (intervalMinutes > 0) {
    if (disables.includes('commentAttachments')) caps.commentAttachments = false;
    if (disables.includes('actionButton')) caps.actionButton = false;
    if (disables.includes('firstComment')) caps.firstComment = false;
    if (disables.includes('scheduledDelete')) caps.scheduledDelete = false;
    if (disables.includes('interval')) caps.interval = false;
  }
  return caps;
}

/** Postponed repost stays disabled until the API marks it available. */
export function isTargetModeAvailable(mode: string, matrix?: GrabberModeMatrix): boolean {
  const row = matrix?.modes.find((item) => item.id === mode);
  if (row && typeof row.available === 'boolean') return row.available;
  return mode !== 'vk_postponed_repost';
}
