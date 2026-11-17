import type { CalendarItem } from '@posts/api-client';

export type PendingMove = {
  postId: string;
  title?: string;
  fromScheduledAt?: string;
  toScheduledAt: string;
  version: number;
  recurrence?: boolean;
};

export type MoveConfirmChoice = 'this_only' | 'all_following' | 'cancel';

/**
 * Build a pending drag-and-drop move. Recurring posts require confirm scope.
 */
export function createPendingMove(params: {
  item: CalendarItem;
  toScheduledAt: string;
  version: number;
  hasRecurrence?: boolean;
}): PendingMove {
  const { item } = params;
  return {
    postId: item.id!,
    title: item.title,
    fromScheduledAt: item.scheduledAt,
    toScheduledAt: params.toScheduledAt,
    version: params.version,
    recurrence: Boolean(params.hasRecurrence),
  };
}

export function resolveMoveChoice(
  pending: PendingMove,
  choice: MoveConfirmChoice,
): { scheduledAt: string; scope: 'this' | 'following' } | null {
  if (choice === 'cancel') return null;
  return {
    scheduledAt: pending.toScheduledAt,
    scope: choice === 'all_following' ? 'following' : 'this',
  };
}

/** Drop target id helpers for dnd-kit. */
export function slotDropId(dayKey: string, hour?: number): string {
  return hour === undefined ? `slot:${dayKey}` : `slot:${dayKey}:${hour}`;
}

export function parseSlotDropId(id: string): { dayKey: string; hour?: number } | null {
  if (!id.startsWith('slot:')) return null;
  const parts = id.slice(5).split(':');
  const day = parts[0];
  if (!day) return null;
  if (parts[1] !== undefined) {
    const hour = Number(parts[1]);
    if (Number.isNaN(hour)) return null;
    return { dayKey: day, hour };
  }
  return { dayKey: day };
}

export function eventDragId(postId: string): string {
  return `event:${postId}`;
}

export function parseEventDragId(id: string): string | null {
  return id.startsWith('event:') ? id.slice(6) : null;
}

/**
 * Combine day + optional hour into ISO datetime, preserving minutes from original when hour set.
 */
export function combineDayAndTime(
  dayKeyValue: string,
  hour: number | undefined,
  originalIso?: string,
): string {
  const base = originalIso ? new Date(originalIso) : new Date(`${dayKeyValue}T10:00:00`);
  const [y, m, d] = dayKeyValue.split('-').map(Number);
  const result = new Date(base);
  result.setFullYear(y!, m! - 1, d!);
  if (hour !== undefined) {
    result.setHours(hour, base.getMinutes(), 0, 0);
  }
  return result.toISOString();
}
