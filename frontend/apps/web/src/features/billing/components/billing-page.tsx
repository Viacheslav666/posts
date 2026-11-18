'use client';

import { Badge, EmptyState, Skeleton } from '@posts/ui';
import { ShieldOff } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { useCan } from '@/shared/hooks/use-can';

import { useBillingUsage } from '../hooks/use-billing-queries';
import { LegalRequisitesForm } from './legal-requisites-form';
import { UsageLimits } from './usage-limits';

type BillingPageProps = {
  workspaceId: string;
};

/**
 * FE-10 scaffold: limits + paywall plumbing + legal requisites form.
 * Balance / top-up / channel tariffs / ledger / autopay — FE-10b after BE-31.
 */
export function BillingPage({ workspaceId }: BillingPageProps) {
  const t = useTranslations('billing');
  const canView = useCan('billing.view', workspaceId);
  const canManage = useCan('billing.manage', workspaceId);
  const usage = useBillingUsage(workspaceId, canView);

  if (!canView) {
    return (
      <EmptyState
        icon={<ShieldOff aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
        data-testid="billing-forbidden"
      />
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6" data-testid="billing-page">
      <div className="space-y-2">
        <h1 className="font-display text-3xl font-semibold tracking-tight text-foreground">
          {t('title')}
        </h1>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
        <p
          className="rounded-lg border border-dashed border-border bg-muted/30 px-3 py-2 text-sm text-muted-foreground"
          data-testid="billing-coming-soon"
        >
          {t('comingSoon')}
        </p>
      </div>

      <section
        className="rounded-xl border border-border bg-gradient-to-br from-muted/40 to-background p-4"
        data-testid="billing-current-plan"
      >
        <p className="text-sm text-muted-foreground">{t('current.label')}</p>
        {usage.isLoading ? (
          <Skeleton className="mt-2 h-8 w-40" />
        ) : (
          <div className="mt-1 flex flex-wrap items-center gap-2">
            <h2 className="font-display text-2xl font-semibold text-foreground">
              {usage.planName ?? usage.planCode}
            </h2>
            <Badge variant="secondary" data-testid="billing-plan-code">
              {usage.planCode}
            </Badge>
          </div>
        )}
        <p className="mt-2 text-xs text-muted-foreground">{t('current.hint')}</p>
      </section>

      <UsageLimits metrics={usage.metrics} isLoading={usage.isLoading} />

      <LegalRequisitesForm workspaceId={workspaceId} canManage={canManage} />
    </div>
  );
}
