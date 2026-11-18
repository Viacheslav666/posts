import { describe, expect, it } from 'vitest';

import {
  addRecurrenceTime,
  isValidRecurrenceTime,
  normalizeRecurrenceTimes,
  RECURRENCE_TIMES_MAX,
  validateRecurrenceTimes,
} from './recurrence-times';

describe('recurrence-times', () => {
  it('validates HH:mm', () => {
    expect(isValidRecurrenceTime('09:00')).toBe(true);
    expect(isValidRecurrenceTime('23:59')).toBe(true);
    expect(isValidRecurrenceTime('24:00')).toBe(false);
    expect(isValidRecurrenceTime('9:00')).toBe(false);
  });

  it('detects duplicates and over-limit', () => {
    expect(validateRecurrenceTimes(['09:00', '09:00']).duplicates).toEqual(['09:00']);
    expect(validateRecurrenceTimes(['09:00', 'bad']).invalid).toEqual(['bad']);
    const many = Array.from({ length: RECURRENCE_TIMES_MAX + 1 }, (_, i) =>
      `${String(Math.floor(i / 60)).padStart(2, '0')}:${String(i % 60).padStart(2, '0')}`,
    );
    expect(validateRecurrenceTimes(many).overLimit).toBe(true);
  });

  it('normalizes and dedupes', () => {
    expect(normalizeRecurrenceTimes(['09:00', '09:00', '18:30', 'xx'])).toEqual([
      '09:00',
      '18:30',
    ]);
  });

  it('addRecurrenceTime skips duplicates and respects max', () => {
    expect(addRecurrenceTime(['09:00'], '09:00')).toEqual(['09:00']);
    expect(addRecurrenceTime(['09:00'], '18:30')).toEqual(['09:00', '18:30']);
  });
});
