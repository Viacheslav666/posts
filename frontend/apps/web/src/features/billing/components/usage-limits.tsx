'use client';

import { Progress, Skeleton } from '@posts/ui';
import { useTranslations } from 'next-intl';

import { formatLimitValue, usagePercent, type UsageMetric } from '../model/limits';

type UsageLimitsProps = {
  metrics: UsageMetric[];
  isLoading?: boolean;
};

export function UsageLimits({ metrics, isLoading }: UsageLimitsProps) {
  const t = useTranslations('billing.limits');

  if (isLoading) {
    return (
      <div className="space-y-3" data-testid="billing-usage-loading">
        <Skeleton className="h-4 w-40" />
        <Skeleton className="h-2 w-full" />
        <Skeleton className="h-2 w-full" />
      </div>
    );
  }

  return (
    <div className="space-y-4" data-testid="billing-usage">
      <h2 className="text-lg font-semibold text-foreground">{t('title')}</h2>
      <ul className="space-y-3">
        {metrics.map((metric) => {
          const percent = usagePercent(metric.used, metric.limit);
          const limitLabel = formatLimitValue(metric.limit);
          return (
            <li key={metric.key} className="space-y-1.5" data-testid={`billing-usage-${metric.key}`}>
              <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
                <span className="font-medium text-foreground">{t(`keys.${metric.key}`)}</span>
                <span className="text-muted-foreground">
                  {t('usage', { used: metric.used, limit: limitLabel })}
                </span>
              </div>
              <Progress
                value={percent}
                aria-label={t('aria', { name: t(`keys.${metric.key}`), percent })}
              />
            </li>
          );
        })}
      </ul>
    </div>
  );
}
