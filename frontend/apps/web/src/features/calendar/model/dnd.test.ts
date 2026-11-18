import { describe, expect, it } from 'vitest';

import {
  combineDayAndTime,
  createPendingMove,
  eventDragId,
  parseEventDragId,
  parseSlotDropId,
  resolveMoveChoice,
  slotDropId,
} from './dnd';

describe('calendar dnd helpers', () => {
  it('builds and parses slot / event ids', () => {
    expect(slotDropId('2026-11-17')).toBe('slot:2026-11-17');
    expect(slotDropId('2026-11-17', 14)).toBe('slot:2026-11-17:14');
    expect(parseSlotDropId('slot:2026-11-17')).toEqual({ dayKey: '2026-11-17' });
    expect(parseSlotDropId('slot:2026-11-17:9')).toEqual({ dayKey: '2026-11-17', hour: 9 });
    expect(parseSlotDropId('x')).toBeNull();
    expect(eventDragId('post-1')).toBe('event:post-1');
    expect(parseEventDragId('event:post-1')).toBe('post-1');
  });

  it('creates pending move and resolves confirm choices', () => {
    const pending = createPendingMove({
      item: {
        id: 'p1',
        title: 'Hello',
        scheduledAt: '2026-11-17T10:00:00.000Z',
      },
      toScheduledAt: '2026-11-18T10:00:00.000Z',
      version: 2,
      hasRecurrence: true,
    });
    expect(pending.recurrence).toBe(true);
    expect(resolveMoveChoice(pending, 'cancel')).toBeNull();
    expect(resolveMoveChoice(pending, 'this_only')).toEqual({
      scheduledAt: '2026-11-18T10:00:00.000Z',
      scope: 'this',
    });
    expect(resolveMoveChoice(pending, 'all_following')?.scope).toBe('following');
  });

  it('combines day key with hour preserving minutes', () => {
    const iso = combineDayAndTime('2026-11-20', 15, '2026-11-17T10:30:00.000Z');
    const d = new Date(iso);
    expect(d.getFullYear()).toBe(2026);
    expect(d.getMonth()).toBe(10);
    expect(d.getDate()).toBe(20);
    expect(d.getHours()).toBe(15);
    expect(d.getMinutes()).toBe(30);
  });
});
