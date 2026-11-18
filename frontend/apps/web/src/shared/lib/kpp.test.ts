import { describe, expect, it } from 'vitest';

import { isValidKpp } from './kpp';

describe('isValidKpp', () => {
  it('accepts a typical 9-digit KPP', () => {
    expect(isValidKpp('773601001')).toBe(true);
  });

  it('rejects wrong length or zero reason code', () => {
    expect(isValidKpp('7736')).toBe(false);
    expect(isValidKpp('770000001')).toBe(false);
  });
});
