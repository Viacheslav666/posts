import { describe, expect, it } from 'vitest';

import {
  buildMonthGrid,
  buildWeekDays,
  dayKey,
  rangeForView,
  shiftAnchor,
} from './date-range';

describe('calendar date-range', () => {
  it('builds month range covering padded weeks', () => {
    const anchor = new Date(2026, 10, 15);
    const range = rangeForView(anchor, 'month');
    expect(new Date(range.from).getTime()).toBeLessThanOrEqual(
      new Date(2026, 10, 1).getTime(),
    );
    expect(new Date(range.to).getTime()).toBeGreaterThanOrEqual(
      new Date(2026, 10, 30).getTime(),
    );
    expect(buildMonthGrid(anchor).length).toBeGreaterThanOrEqual(28);
  });

  it('shifts week/day anchors', () => {
    const anchor = new Date(2026, 10, 17);
    expect(dayKey(shiftAnchor(anchor, 'day', 1))).toBe('2026-11-18');
    expect(buildWeekDays(anchor)).toHaveLength(7);
    const week = rangeForView(anchor, 'week');
    expect(new Date(week.from).getDay()).toBe(1);
  });
});
