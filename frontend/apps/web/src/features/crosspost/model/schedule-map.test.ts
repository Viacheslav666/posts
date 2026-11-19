import { describe, expect, it } from 'vitest';

import {
  backendScheduleToWindow,
  isoDayToJs,
  jsDayToIso,
  scheduleWindowToBackend,
} from './schedule-map';

describe('schedule-map', () => {
  it('maps ISO weekdays to JS Date#getDay', () => {
    expect(isoDayToJs(1)).toBe(1);
    expect(isoDayToJs(7)).toBe(0);
    expect(jsDayToIso(0)).toBe(7);
    expect(jsDayToIso(1)).toBe(1);
  });

  it('converts ScheduleWindowEditor intervals to backend hours_from/hours_to', () => {
    const backend = scheduleWindowToBackend(
      {
        days: [1, 2, 3, 4, 5],
        intervals: [
          { start: '09:00', end: '12:00' },
          { start: '14:00', end: '18:00' },
        ],
        timezone: 'Europe/Moscow',
      },
      true,
    );
    expect(backend.enabled).toBe(true);
    expect(backend.days_of_week).toEqual([1, 2, 3, 4, 5]);
    expect(backend.hours_from).toBe(9);
    expect(backend.hours_to).toBe(18);
    expect(backend.timezone).toBe('Europe/Moscow');
  });

  it('treats empty intervals as full day 0–24', () => {
    const backend = scheduleWindowToBackend(
      { days: [0, 6], intervals: [], timezone: 'Asia/Yekaterinburg' },
      false,
    );
    expect(backend.enabled).toBe(false);
    expect(backend.days_of_week).toEqual([6, 7]);
    expect(backend.hours_from).toBe(0);
    expect(backend.hours_to).toBe(24);
  });

  it('round-trips backend schedule through window editor shape', () => {
    const { window, enabled } = backendScheduleToWindow({
      enabled: true,
      timezone: 'Europe/Moscow',
      days_of_week: [1, 3, 7],
      hours_from: 10,
      hours_to: 16,
    });
    expect(enabled).toBe(true);
    expect(window.days).toEqual([0, 1, 3]);
    expect(window.intervals).toEqual([{ start: '10:00', end: '16:00' }]);
    const again = scheduleWindowToBackend(window, enabled);
    expect(again.days_of_week).toEqual([1, 3, 7]);
    expect(again.hours_from).toBe(10);
    expect(again.hours_to).toBe(16);
  });

  it('maps hours_to 24 to 23:59 end in editor', () => {
    const { window } = backendScheduleToWindow({
      enabled: true,
      timezone: 'Europe/Moscow',
      days_of_week: [1],
      hours_from: 8,
      hours_to: 24,
    });
    expect(window.intervals[0]?.end).toBe('23:59');
  });
});
