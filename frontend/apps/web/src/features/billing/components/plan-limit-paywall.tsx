'use client';

import { Button } from '@posts/ui';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

import type { LimitKey } from '../model/limits';

type PlanLimitPaywallProps = {
  workspaceId: string;
  variant?: 'soft' | 'hard';
  limitKey?: LimitKey | string;
  used?: number;
  limit?: number;
  className?: string;
};

export function PlanLimitPaywall({
  workspaceId,
  variant = 'soft',
  limitKey,
  used,
  limit,
  className,
}: PlanLimitPaywallProps) {
  const t = useTranslations('billing.paywall');
  const tLimits = useTranslations('billing.limits');
  const keyed =
    limitKey &&
    (['channels', 'seats', 'posts_month', 'ai_credits', 'grabber_rules', 'stories_month'].includes(
      limitKey,
    )
      ? tLimits(`keys.${limitKey as 'channels'}`)
      : limitKey);
  const title =
    variant === 'hard'
      ? t('hardTitle')
      : keyed
        ? t('softTitleKeyed', { key: keyed })
        : t('softTitle');

  return (
    <div
      className={
        className ??
        'flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border bg-muted/40 px-4 py-3'
      }
      data-testid={variant === 'hard' ? 'plan-limit-paywall-hard' : 'plan-limit-paywall'}
      role="status"
    >
      <div>
        <p className="text-sm font-medium text-foreground">{title}</p>
        <p className="text-sm text-muted-foreground">
          {typeof used === 'number' && typeof limit === 'number'
            ? t('usage', { used, limit })
            : t('body')}
        </p>
      </div>
      <Button asChild size="sm">
        <Link href={`/app/${workspaceId}/billing`} data-testid="plan-limit-upgrade">
          {t('upgrade')}
        </Link>
      </Button>
    </div>
  );
}
