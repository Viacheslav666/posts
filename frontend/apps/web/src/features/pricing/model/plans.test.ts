import { describe, expect, it } from 'vitest';

import { formatRub, PLANS, priceForPeriod } from './plans';

describe('pricing plans', () => {
  it('exposes PRD MVP plans', () => {
    expect(PLANS.map((plan) => plan.id)).toEqual(['free', 'start', 'pro', 'agency']);
    expect(PLANS.find((plan) => plan.id === 'pro')?.monthlyPrice).toBe(990);
  });

  it('applies yearly discount', () => {
    expect(priceForPeriod(990, false)).toBe(990);
    expect(priceForPeriod(990, true)).toBe(9504);
    expect(priceForPeriod(0, true)).toBe(0);
  });

  it('formats rub amounts for ru-RU', () => {
    expect(formatRub(2990)).toMatch(/2.?990/);
  });
});
