import { describe, expect, it } from 'vitest';

import { shouldResetDeleteAfterManual } from './ad-delete-after-manual';

describe('shouldResetDeleteAfterManual', () => {
  const enabled30 = { enabled: true, displayPeriodDays: 30 };
  const disabled30 = { enabled: false, displayPeriodDays: 30 };

  it('does not reset when only other ad fields change', () => {
    expect(shouldResetDeleteAfterManual(enabled30, enabled30)).toBe(false);
  });

  it('resets when auto-delete is re-enabled', () => {
    expect(shouldResetDeleteAfterManual(disabled30, enabled30)).toBe(true);
  });

  it('resets when display period changes', () => {
    expect(shouldResetDeleteAfterManual(enabled30, { enabled: true, displayPeriodDays: 14 })).toBe(
      true,
    );
  });
});