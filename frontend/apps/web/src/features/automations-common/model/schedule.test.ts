import { describe, expect, it } from 'vitest';

import { isValidTime, toggleWeekday } from './schedule';

describe('schedule model', () => {
  it('validates HH:mm times', () => {
    expect(isValidTime('09:00')).toBe(true);
    expect(isValidTime('23:59')).toBe(true);
    expect(isValidTime('24:00')).toBe(false);
    expect(isValidTime('9:00')).toBe(false);
  });

  it('toggles weekdays', () => {
    expect(toggleWeekday([1, 2], 3)).toEqual([1, 2, 3]);
    expect(toggleWeekday([1, 2, 3], 2)).toEqual([1, 3]);
  });
});
