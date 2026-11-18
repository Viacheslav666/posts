import type { AdMarkingVatRate, VatBreakdown } from '@posts/api-client';

/** VAT rates supported by BE-25 / OpenAPI `vatRate` enum. */
export const VAT_RATES: readonly AdMarkingVatRate[] = [
  'without_vat',
  '0',
  '5',
  '7',
  '10',
  '20',
  '22',
] as const;

function round2(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

function percentOf(rate: AdMarkingVatRate): number | null {
  if (rate === 'without_vat' || rate === '0') return null;
  return Number(rate);
}

/**
 * Client mirror of backend `VatCalculator.fromAmountWithVat` (amount includes VAT).
 * Used for instant UI feedback; prefer server `/ord/vat/calculate` when available.
 */
export function calculateVatBreakdown(
  amountWithVat: number,
  vatRate: AdMarkingVatRate | string | undefined,
): VatBreakdown {
  const rate = (vatRate && VAT_RATES.includes(vatRate as AdMarkingVatRate)
    ? vatRate
    : 'without_vat') as AdMarkingVatRate;
  const gross = round2(Number.isFinite(amountWithVat) ? Math.max(0, amountWithVat) : 0);
  const percent = percentOf(rate);
  if (percent === null || percent === 0) {
    return {
      amountWithVat: gross,
      amountWithoutVat: gross,
      vatAmount: 0,
      vatRate: rate,
    };
  }
  const net = round2((gross * 100) / (100 + percent));
  const vat = round2(gross - net);
  return {
    amountWithVat: gross,
    amountWithoutVat: net,
    vatAmount: vat,
    vatRate: rate,
  };
}
