/** Копейки → рубли (число). */
export function kopecksToRubles(kopecks: number): number {
  return kopecks / 100;
}

/** Рубли → копейки (целое). */
export function rublesToKopecks(rubles: number): number {
  return Math.round(rubles * 100);
}

/** Форматирование суммы в рублях для UI (ru-RU). */
export function formatRubAmount(rubles: number, options?: { withCurrency?: boolean }): string {
  const formatted = new Intl.NumberFormat('ru-RU', {
    minimumFractionDigits: Number.isInteger(rubles) ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(rubles);
  return options?.withCurrency === false ? formatted : `${formatted}\u00a0₽`;
}

/** Форматирование копеек как рублей. */
export function formatKopecks(kopecks: number, options?: { withCurrency?: boolean; signed?: boolean }): string {
  const rubles = kopecksToRubles(kopecks);
  const absolute = formatRubAmount(Math.abs(rubles), options);
  if (options?.signed && kopecks !== 0) {
    return kopecks > 0 ? `+${absolute}` : `−${absolute}`;
  }
  return absolute;
}

/** Мин. сумма пополнения (как на бэке: autopayMinTopupKopecks = 10_000). */
export const MIN_TOPUP_KOPECKS = 10_000;

/** Пресеты пополнения в рублях (как vposter: default 2000). */
export const TOPUP_PRESETS_RUB = [500, 1000, 2000, 5000] as const;

export const DEFAULT_TOPUP_RUB = 2000;

/** Порог «мало средств»: daysRemaining < 3 (billing-rules §6). */
export const LOW_BALANCE_DAYS_THRESHOLD = 3;

export function isLowBalance(wallet: {
  billingStatus?: string | null;
  daysRemaining?: number | null;
  balanceKopecks?: number | null;
  priceDayKopecks?: number | null;
}): boolean {
  if (wallet.billingStatus === 'frozen') {
    return false;
  }
  if (typeof wallet.daysRemaining === 'number') {
    return wallet.daysRemaining < LOW_BALANCE_DAYS_THRESHOLD;
  }
  const balance = wallet.balanceKopecks ?? 0;
  const priceDay = wallet.priceDayKopecks ?? 0;
  if (priceDay <= 0) {
    return false;
  }
  return Math.floor(balance / priceDay) < LOW_BALANCE_DAYS_THRESHOLD;
}
