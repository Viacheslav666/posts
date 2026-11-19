import { describe, expect, it } from 'vitest';

import { clampLimitAmount } from './limits';

describe('limits model', () => {
  it('clamps amounts to non-negative integers', () => {
    expect(clampLimitAmount(3.7)).toBe(3);
    expect(clampLimitAmount(-1)).toBe(0);
    expect(clampLimitAmount(null)).toBeNull();
    expect(clampLimitAmount(Number.NaN)).toBeNull();
  });
});
