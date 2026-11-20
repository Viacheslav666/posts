import { describe, expect, it } from 'vitest';

import { createDefaultAdMarking } from '../model/defaults';
import { AD_DEFAULT_DISPLAY_PERIOD_DAYS, defaultAdDeleteAfterIso } from './ad-delete-after';

describe('ad-delete-after', () => {
  it('uses display period from marking', () => {
    const publish = '2026-01-01T12:00:00.000Z';
    const ad = { ...createDefaultAdMarking(), displayPeriodDays: 14 };
    const result = defaultAdDeleteAfterIso(publish, ad);
    expect(result).toBe(new Date(Date.parse(publish) + 14 * 86_400_000).toISOString());
  });

  it('defaults to 30 days constant', () => {
    expect(AD_DEFAULT_DISPLAY_PERIOD_DAYS).toBe(30);
    const publish = '2026-01-01T12:00:00.000Z';
    const ad = createDefaultAdMarking();
    const result = defaultAdDeleteAfterIso(publish, ad);
    expect(result).toBe(
      new Date(Date.parse(publish) + AD_DEFAULT_DISPLAY_PERIOD_DAYS * 86_400_000).toISOString(),
    );
  });

  it('returns undefined when auto delete is off', () => {
    const ad = { ...createDefaultAdMarking(), autoDeleteAfterPeriod: false };
    expect(defaultAdDeleteAfterIso('2026-01-01T12:00:00.000Z', ad)).toBeUndefined();
  });
});