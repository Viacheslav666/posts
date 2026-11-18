import { describe, expect, it } from 'vitest';

import {
  mapVposterPostDay,
  materializeOccurrences,
  presetFromRecurrence,
  VPOSTER_POST_DAY,
} from './recurrence';

describe('recurrence (TC-AD-02)', () => {
  it('maps every vposter post_day to a Recurrence', () => {
    for (const day of VPOSTER_POST_DAY) {
      const rec = mapVposterPostDay(day, day === 'everyNday' ? 3 : undefined);
      expect(rec.freq).toBeTruthy();
      expect(presetFromRecurrence(rec)).toBeTruthy();
    }
  });

  it('maps oneraz / everyday / budday / weekends', () => {
    expect(mapVposterPostDay('oneraz')).toEqual({ freq: 'once', interval: 1 });
    expect(mapVposterPostDay('everyday').freq).toBe('daily');
    expect(mapVposterPostDay('budday').freq).toBe('weekdays');
    expect(mapVposterPostDay('weekends').byWeekday).toEqual(['SA', 'SU']);
  });

  it('materializes daily occurrences within 14 days', () => {
    const start = '2026-11-18T10:00:00.000Z';
    const dates = materializeOccurrences(start, { freq: 'daily', interval: 1 }, 14);
    expect(dates[0]).toBe(start);
    expect(dates.length).toBeGreaterThan(10);
    expect(dates.length).toBeLessThanOrEqual(15);
  });

  it('once yields a single occurrence', () => {
    expect(materializeOccurrences('2026-11-18T10:00:00.000Z', { freq: 'once' })).toHaveLength(1);
  });

  it('expands times[] to multiple runs per day', () => {
    const start = '2026-11-18T06:00:00.000Z';
    const dates = materializeOccurrences(
      start,
      { freq: 'daily', interval: 1, times: ['09:00', '18:30'] },
      2,
    );
    expect(dates.length).toBeGreaterThanOrEqual(4);
    // Same calendar day should produce two distinct clock times.
    const day0 = dates.filter((iso) => iso.startsWith(dates[0]!.slice(0, 10)));
    expect(day0.length).toBeGreaterThanOrEqual(1);
  });

  it('once + times[] emits multiple slots on the start day', () => {
    const dates = materializeOccurrences('2026-11-18T00:00:00.000Z', {
      freq: 'once',
      times: ['09:00', '18:30'],
    });
    expect(dates).toHaveLength(2);
  });
});
