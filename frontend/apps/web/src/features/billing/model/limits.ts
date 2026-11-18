import type { Plan } from '@posts/api-client';

export type LimitKey =
  | 'channels'
  | 'seats'
  | 'posts_month'
  | 'ai_credits'
  | 'grabber_rules'
  | 'stories_month';

export type UsageMetric = {
  key: LimitKey;
  used: number;
  limit: number | null;
};

export const LIMIT_KEYS: LimitKey[] = [
  'channels',
  'seats',
  'posts_month',
  'ai_credits',
  'grabber_rules',
  'stories_month',
];

export function readPlanLimit(plan: Plan | undefined, key: LimitKey): number | null {
  const raw = plan?.limits?.[key];
  if (raw === null || raw === undefined) {
    return null;
  }
  if (typeof raw === 'number') {
    return Number.isFinite(raw) ? raw : null;
  }
  if (typeof raw === 'object' && raw !== null && 'value' in raw) {
    const value = (raw as { value?: unknown }).value;
    return typeof value === 'number' ? value : null;
  }
  return null;
}

export function isAtLimit(used: number, limit: number | null): boolean {
  if (limit === null) {
    return false;
  }
  return used >= limit;
}

export function usagePercent(used: number, limit: number | null): number {
  if (limit === null || limit <= 0) {
    return 0;
  }
  return Math.min(100, Math.round((used / limit) * 100));
}

export function formatLimitValue(limit: number | null): string {
  if (limit === null) {
    return '∞';
  }
  return new Intl.NumberFormat('ru-RU').format(limit);
}
