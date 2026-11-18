/**
 * Russian INN (ИНН) checksum validation — 10 digits (legal entity) or 12 (individual).
 * Spec: FE-12 / scheduled-posts-and-ad-marking.md §3.4.
 */

const COEFF_10 = [2, 4, 10, 3, 5, 9, 4, 6, 8] as const;
const COEFF_11 = [7, 2, 4, 10, 3, 5, 9, 4, 6, 8] as const;
const COEFF_12 = [3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8] as const;

function digitChecksum(digits: number[], coeffs: readonly number[]): number {
  const sum = coeffs.reduce((acc, coeff, index) => acc + coeff * (digits[index] ?? 0), 0);
  return (sum % 11) % 10;
}

export function normalizeInn(value: string): string {
  return value.replace(/\D/g, '');
}

export function isValidInn(value: string): boolean {
  const inn = normalizeInn(value);
  if (!/^\d{10}$|^\d{12}$/.test(inn)) {
    return false;
  }
  const digits = [...inn].map((ch) => Number(ch));
  if (inn.length === 10) {
    return digitChecksum(digits, COEFF_10) === digits[9];
  }
  return (
    digitChecksum(digits, COEFF_11) === digits[10] &&
    digitChecksum(digits, COEFF_12) === digits[11]
  );
}
