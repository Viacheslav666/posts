import { describe, expect, it } from 'vitest';

import { calculateVatBreakdown, VAT_RATES } from './vat-calculator';

describe('calculateVatBreakdown', () => {
  it('exposes BE-25 vat rate enum', () => {
    expect(VAT_RATES).toEqual(['without_vat', '0', '5', '7', '10', '20', '22']);
  });

  it('keeps net equal to gross when without_vat', () => {
    expect(calculateVatBreakdown(1000, 'without_vat')).toEqual({
      amountWithVat: 1000,
      amountWithoutVat: 1000,
      vatAmount: 0,
      vatRate: 'without_vat',
    });
  });

  it('keeps net equal to gross for 0% rate', () => {
    expect(calculateVatBreakdown(250.5, '0')).toEqual({
      amountWithVat: 250.5,
      amountWithoutVat: 250.5,
      vatAmount: 0,
      vatRate: '0',
    });
  });

  it('splits 20% VAT from amount including tax (HALF_UP to 2 dp)', () => {
    expect(calculateVatBreakdown(120, '20')).toEqual({
      amountWithVat: 120,
      amountWithoutVat: 100,
      vatAmount: 20,
      vatRate: '20',
    });
  });

  it('splits 22% VAT', () => {
    expect(calculateVatBreakdown(122, '22')).toEqual({
      amountWithVat: 122,
      amountWithoutVat: 100,
      vatAmount: 22,
      vatRate: '22',
    });
  });

  it('splits 10% with rounding', () => {
    expect(calculateVatBreakdown(100, '10')).toEqual({
      amountWithVat: 100,
      amountWithoutVat: 90.91,
      vatAmount: 9.09,
      vatRate: '10',
    });
  });

  it('clamps negative and non-finite amounts to 0', () => {
    expect(calculateVatBreakdown(-5, '20').amountWithVat).toBe(0);
    expect(calculateVatBreakdown(Number.NaN, '5').amountWithVat).toBe(0);
  });

  it('falls back to without_vat for unknown rate', () => {
    expect(calculateVatBreakdown(50, '99' as never).vatRate).toBe('without_vat');
  });
});
