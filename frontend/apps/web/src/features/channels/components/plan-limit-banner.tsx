'use client';

import { Button } from '@posts/ui';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

type PlanLimitBannerProps = {
  used: number;
  limit: number;
  workspaceId: string;
  showUpgrade?: boolean;
};

export function PlanLimitBanner({
  used,
  limit,
  workspaceId,
  showUpgrade = false,
}: PlanLimitBannerProps) {
  const t = useTranslations('channels');
  const atLimit = used >= limit;

  return (
    <div
      className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border bg-muted/40 px-4 py-3"
      data-testid="plan-limit-banner"
      role="status"
    >
      <div>
        <p className="text-sm font-medium text-foreground">
          {t('planLimit.usage', { used, limit })}
        </p>
        {atLimit || showUpgrade ? (
          <p className="text-sm text-muted-foreground">{t('planLimit.body')}</p>
        ) : null}
      </div>
      {(atLimit || showUpgrade) && (
        <Button asChild size="sm">
          <Link href={`/app/${workspaceId}/billing`} data-testid="plan-limit-upgrade">
            {t('planLimit.upgrade')}
          </Link>
        </Button>
      )}
    </div>
  );
}
