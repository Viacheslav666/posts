import { describe, expect, it } from 'vitest';

import {
  formatKopecks,
  isLowBalance,
  kopecksToRubles,
  rublesToKopecks,
} from './money';

describe('billing money helpers', () => {
  it('converts rubles and kopecks', () => {
    expect(rublesToKopecks(2000)).toBe(200_000);
    expect(kopecksToRubles(2700)).toBe(27);
  });

  it('formats signed amounts', () => {
    expect(formatKopecks(2700, { signed: true })).toContain('+');
    expect(formatKopecks(-2700, { signed: true })).toContain('−');
  });

  it('detects low balance by daysRemaining', () => {
    expect(
      isLowBalance({ billingStatus: 'active', daysRemaining: 2, balanceKopecks: 5400, priceDayKopecks: 2700 }),
    ).toBe(true);
    expect(
      isLowBalance({ billingStatus: 'active', daysRemaining: 10, balanceKopecks: 27_000, priceDayKopecks: 2700 }),
    ).toBe(false);
    expect(isLowBalance({ billingStatus: 'frozen', daysRemaining: 0 })).toBe(false);
  });
});
