/**
 * Russian KPP (КПП) — 9 digits for legal entities.
 * Format: NNNN PP XXX (tax office + reason + serial).
 */

export function normalizeKpp(value: string): string {
  return value.replace(/\D/g, '');
}

export function isValidKpp(value: string): boolean {
  const kpp = normalizeKpp(value);
  if (!/^\d{9}$/.test(kpp)) {
    return false;
  }
  // Reason codes 01–99; leading zeros allowed for tax office.
  const reason = Number(kpp.slice(4, 6));
  return reason >= 1 && reason <= 99;
}
