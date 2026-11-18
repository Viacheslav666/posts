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

/** Lightweight plan code chip (not a subscription/balance widget — those land in FE-10b). */
export function HeaderPlanChip({ workspaceId }: HeaderPlanChipProps) {
  const t = useTranslations('billing.header');
  const canView = useCan('billing.view', workspaceId);

  const workspaceQuery = useQuery({
    queryKey: queryKeys.workspaces.detail(workspaceId),
    queryFn: () => getWorkspace(workspaceId),
  });

  if (workspaceQuery.isLoading) {
    return <Skeleton className="hidden h-7 w-16 sm:block" />;
  }

  const planCode = workspaceQuery.data?.planCode ?? 'free';
  const chip = (
    <Badge variant="secondary" className="hidden capitalize sm:inline-flex" data-testid="header-plan-chip">
      {t('plan', { plan: planCode })}
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
