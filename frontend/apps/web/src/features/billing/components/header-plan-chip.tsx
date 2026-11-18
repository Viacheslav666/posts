'use client';

import { Badge, Skeleton } from '@posts/ui';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';
import { useQuery } from '@tanstack/react-query';

import { getWallet } from '../api/billing-api';
import { formatKopecks, isLowBalance } from '../model/money';

type HeaderPlanChipProps = {
  workspaceId: string;
};

const PLAN_NAME_KEYS = new Set(['free', 'start', 'pro', 'agency']);

/** Баланс / тариф в шапке (FE-10b) с русским названием тарифа. */
export function HeaderPlanChip({ workspaceId }: HeaderPlanChipProps) {
  const t = useTranslations('billing.header');
  const tPlans = useTranslations('pricing.plans');
  const canView = useCan('billing.view', workspaceId);

  const walletQuery = useQuery({
    queryKey: queryKeys.billing.wallet(workspaceId),
    queryFn: () => getWallet(workspaceId),
    enabled: canView,
    staleTime: 30_000,
  });

  if (!canView) {
    return null;
  }

  if (walletQuery.isLoading) {
    return <Skeleton className="hidden h-7 w-24 sm:block" />;
  }

  const wallet = walletQuery.data;
  const planCode = (wallet?.planCode ?? 'free').toLowerCase();
  const planLabel = PLAN_NAME_KEYS.has(planCode)
    ? tPlans(`${planCode}.name` as 'pro.name')
    : planCode;
  const frozen = wallet?.billingStatus === 'frozen';
  const low = wallet ? isLowBalance(wallet) : false;
  const balanceLabel =
    wallet?.balanceKopecks != null ? formatKopecks(wallet.balanceKopecks) : planLabel;

  const chip = (
    <Badge
      variant={frozen ? 'failed' : 'secondary'}
      className="hidden max-w-[12rem] truncate sm:inline-flex"
      data-testid="header-plan-chip"
      data-frozen={frozen ? 'true' : 'false'}
      data-low={low ? 'true' : 'false'}
    >
      {t('balance', { balance: balanceLabel, plan: planLabel })}
    </Badge>
  );

  return (
    <Link
      href={`/app/${workspaceId}/billing`}
      className="rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      aria-label={t('aria')}
    >
      {chip}
    </Link>
  );
}
