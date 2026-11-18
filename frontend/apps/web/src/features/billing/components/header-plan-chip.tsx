'use client';

import { Badge, Skeleton } from '@posts/ui';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

import { getWorkspace } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';

type HeaderPlanChipProps = {
  workspaceId: string;
};

const PLAN_NAME_KEYS = new Set(['free', 'start', 'pro', 'agency']);

/** Lightweight plan code chip (not a subscription/balance widget — those land in FE-10b). */
export function HeaderPlanChip({ workspaceId }: HeaderPlanChipProps) {
  const t = useTranslations('billing.header');
  const tPlans = useTranslations('pricing.plans');
  const canView = useCan('billing.view', workspaceId);

  const workspaceQuery = useQuery({
    queryKey: queryKeys.workspaces.detail(workspaceId),
    queryFn: () => getWorkspace(workspaceId),
  });

  if (workspaceQuery.isLoading) {
    return <Skeleton className="hidden h-7 w-16 sm:block" />;
  }

  const planCode = (workspaceQuery.data?.planCode ?? 'free').toLowerCase();
  const planLabel = PLAN_NAME_KEYS.has(planCode)
    ? tPlans(`${planCode}.name` as 'pro.name')
    : planCode;
  const chip = (
    <Badge variant="secondary" className="hidden sm:inline-flex" data-testid="header-plan-chip">
      {t('plan', { plan: planLabel })}
    </Badge>
  );

  if (!canView) {
    return chip;
  }

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
