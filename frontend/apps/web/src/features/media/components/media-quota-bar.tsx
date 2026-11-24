'use client';

import { useTranslations } from 'next-intl';

import type { MediaQuota } from '../model/types';

function formatBytes(value: number): string {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  if (value < 1024 * 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB`;
  return `${(value / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

type MediaQuotaBarProps = {
  quota?: MediaQuota;
};

export function MediaQuotaBar({ quota }: MediaQuotaBarProps) {
  const t = useTranslations('media.quota');
  if (!quota) {
    return null;
  }
  const ratio = quota.limitBytes > 0 ? Math.min(1, quota.usedBytes / quota.limitBytes) : 0;
  const percent = Math.round(ratio * 100);

  return (
    <div className="space-y-1" data-testid="media-quota">
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
        <span>{t('label')}</span>
        <span>
          {quota.limitCount > 0
            ? t('usage', {
                used: formatBytes(quota.usedBytes),
                limit: formatBytes(quota.limitBytes),
                count: quota.usedCount,
                max: quota.limitCount,
              })
            : t('usageBytes', {
                used: formatBytes(quota.usedBytes),
                limit: formatBytes(quota.limitBytes),
              })}
        </span>
      </div>
      <div
        className="h-2 overflow-hidden rounded-full bg-muted"
        role="progressbar"
        aria-valuenow={percent}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={t('label')}
      >
        <div
          className="h-full rounded-full bg-primary transition-[width]"
          style={{ width: `${percent}%` }}
        />
      </div>
    </div>
  );
}
