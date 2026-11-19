export type LimitPeriod = 'hour' | 'day' | 'week';

export type LimitValue = {
  amount: number | null;
  period: LimitPeriod;
};

export const LIMIT_PERIODS: LimitPeriod[] = ['hour', 'day', 'week'];

export function clampLimitAmount(raw: number | null | undefined): number | null {
  if (raw == null || Number.isNaN(raw)) {
    return null;
  }
  if (raw < 0) return 0;
  return Math.floor(raw);
}
