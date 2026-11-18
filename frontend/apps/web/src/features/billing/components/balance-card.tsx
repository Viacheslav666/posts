'use client';

import type { Wallet } from '@posts/api-client';
import { Badge, Skeleton } from '@posts/ui';
import { AlertTriangle, Snowflake } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { formatKopecks, isLowBalance } from '../model/money';

type BalanceCardProps = {
  wallet?: Wallet;
  planName?: string;
  isLoading?: boolean;
};

export function BalanceCard({ wallet, planName, isLoading }: BalanceCardProps) {
  const t = useTranslations('billing.balance');

  if (isLoading || !wallet) {
    return (
      <section
        className="space-y-3 rounded-xl border border-border bg-gradient-to-br from-muted/40 to-background p-4"
        data-testid="billing-balance-loading"
      >
        <Skeleton className="h-4 w-32" />
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-4 w-56" />
      </section>
    );
  }

  const frozen = wallet.billingStatus === 'frozen';
  const low = isLowBalance(wallet);
  const days = wallet.daysRemaining;

  return (
    <section
      className="space-y-3 rounded-xl border border-border bg-gradient-to-br from-muted/40 to-background p-4"
      data-testid="billing-balance"
      aria-live="polite"
    >
      <div className="flex flex-wrap items-center gap-2">
        <p className="text-sm text-muted-foreground">{t('label')}</p>
        {frozen ? (
          <Badge variant="failed" data-testid="billing-status-frozen">
            <Snowflake className="mr-1 h-3 w-3" aria-hidden />
            {t('status.frozen')}
          </Badge>
        ) : (
          <Badge variant="secondary" data-testid={`billing-status-${wallet.billingStatus ?? 'active'}`}>
            {t(`status.${wallet.billingStatus ?? 'active'}`)}
          </Badge>
        )}
      </div>

      <p
        className="font-display text-3xl font-semibold tracking-tight text-foreground"
        data-testid="billing-balance-amount"
      >
        {formatKopecks(wallet.balanceKopecks ?? 0)}
      </p>

      <dl className="grid gap-2 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-muted-foreground">{t('dailyRate')}</dt>
          <dd className="font-medium text-foreground" data-testid="billing-daily-rate">
            {(wallet.priceDayKopecks ?? 0) > 0
              ? t('dailyRateValue', { amount: formatKopecks(wallet.priceDayKopecks ?? 0) })
              : t('dailyRateFree')}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{t('daysRemaining')}</dt>
          <dd className="font-medium text-foreground" data-testid="billing-days-remaining">
            {typeof days === 'number' ? t('daysValue', { days }) : t('daysUnlimited')}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{t('plan')}</dt>
          <dd className="font-medium text-foreground" data-testid="billing-plan-code">
            {planName ?? wallet.planCode}
          </dd>
        </div>
        {(wallet.bonusBalanceKopecks ?? 0) > 0 ? (
          <div>
            <dt className="text-muted-foreground">{t('bonus')}</dt>
            <dd className="font-medium text-foreground" data-testid="billing-bonus">
              {formatKopecks(wallet.bonusBalanceKopecks ?? 0)}
            </dd>
          </div>
        ) : null}
      </dl>

      {frozen ? (
        <div
          role="alert"
          className="flex gap-2 rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
          data-testid="billing-frozen-alert"
        >
          <Snowflake className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <div>
            <p className="font-medium">{t('frozenTitle')}</p>
            <p>{t('frozenBody')}</p>
          </div>
        </div>
      ) : null}

      {!frozen && low ? (
        <div
          role="status"
          className="flex gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm text-amber-900 dark:text-amber-100"
          data-testid="billing-low-balance-alert"
        >
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <div>
            <p className="font-medium">{t('lowTitle')}</p>
            <p>{t('lowBody')}</p>
          </div>
        </div>
      ) : null}
    </section>
  );
}
