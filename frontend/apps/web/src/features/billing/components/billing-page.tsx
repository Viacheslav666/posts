'use client';

import { EmptyState } from '@posts/ui';
import { ShieldOff } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { useCan } from '@/shared/hooks/use-can';

import { useBillingUsage } from '../hooks/use-billing-queries';
import { AutopayForm } from './autopay-form';
import { BalanceCard } from './balance-card';
import { InvoicesPanel } from './invoices-panel';
import { LegalRequisitesForm } from './legal-requisites-form';
import { PlanPicker } from './plan-picker';
import { TopupForm } from './topup-form';
import { TransactionsHistory } from './transactions-history';
import { UsageLimits } from './usage-limits';

type BillingPageProps = {
  workspaceId: string;
};

/**
 * FE-10b: баланс + посуточное списание, пополнение ЮKassa, автоплатёж, история, счета.
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
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-6" data-testid="billing-page">
      <div className="space-y-2">
        <h1 className="font-display text-3xl font-semibold tracking-tight text-foreground">{t('title')}</h1>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
      </div>

      <BalanceCard wallet={usage.wallet} planName={usage.planName} isLoading={usage.isLoading} />

      <TopupForm workspaceId={workspaceId} canManage={canManage} />

      <div id="billing-plan">
        <PlanPicker workspaceId={workspaceId} currentPlanCode={usage.planCode} canManage={canManage} />
      </div>

      <AutopayForm workspaceId={workspaceId} canManage={canManage} />

      <div id="billing-payments">
        <TransactionsHistory workspaceId={workspaceId} />
      </div>

      <div id="billing-documents">
        <InvoicesPanel workspaceId={workspaceId} canManage={canManage} />
      </div>

      <UsageLimits metrics={usage.metrics} isLoading={usage.isLoading} />

      <LegalRequisitesForm workspaceId={workspaceId} canManage={canManage} />
    </div>
  );
}
