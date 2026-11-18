'use client';

import {
  Badge,
  Button,
  ConfirmActionDialog,
  EmptyState,
  Input,
  Skeleton,
  toast,
} from '@posts/ui';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import { useState } from 'react';

import { getErrorMessage } from '@/shared/lib/errors';

import {
  useAdjustBalanceMutation,
  useAdjustSubscriptionMutation,
  useAdminWorkspace,
  useIsPlatformAdmin,
} from '../hooks/use-admin-queries';

type Props = { workspaceId: string };

export function AdminWorkspaceDetailPage({ workspaceId }: Props) {
  const t = useTranslations('admin.workspaces');
  const { isPlatformAdmin } = useIsPlatformAdmin();
  const query = useAdminWorkspace(workspaceId, isPlatformAdmin);
  const adjustSub = useAdjustSubscriptionMutation(workspaceId);
  const adjustBal = useAdjustBalanceMutation(workspaceId);

  const [planCode, setPlanCode] = useState('');
  const [reason, setReason] = useState('');
  const [amount, setAmount] = useState('100');
  const [confirmSub, setConfirmSub] = useState(false);
  const [confirmBal, setConfirmBal] = useState(false);

  if (query.isLoading) return <Skeleton className="h-48 w-full" />;
  if (query.isError || !query.data) {
    return <EmptyState title={t('detailErrorTitle')} description={t('detailErrorBody')} />;
  }

  const ws = query.data;

  return (
    <div className="space-y-6" data-testid="admin-workspace-detail">
      <div>
        <Link href="/admin/workspaces" className="text-sm text-muted-foreground hover:text-foreground">
          ← {t('back')}
        </Link>
        <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight">{ws.name}</h1>
        <div className="mt-2 flex flex-wrap gap-2">
          <Badge variant="secondary">{ws.planCode}</Badge>
          <Badge>{ws.billingStatus}</Badge>
          <Badge variant="outline">
            {t('balance')}: {ws.balance ?? 0}
          </Badge>
        </div>
        <p className="mt-2 text-sm text-muted-foreground">
          {t('owner')}: {ws.ownerEmail}
        </p>
      </div>

      <section className="space-y-3 rounded-xl border border-border p-4">
        <h2 className="font-semibold">{t('adjustPlanTitle')}</h2>
        <Input
          value={planCode || ws.planCode || ''}
          onChange={(e) => setPlanCode(e.target.value)}
          placeholder={t('planCode')}
          data-testid="admin-ws-plan-code"
        />
        <Input
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder={t('reason')}
          data-testid="admin-ws-plan-reason"
        />
        <Button
          type="button"
          disabled={reason.trim().length < 3}
          onClick={() => setConfirmSub(true)}
          data-testid="admin-ws-plan-open"
        >
          {t('adjustPlan')}
        </Button>
      </section>

      <section className="space-y-3 rounded-xl border border-border p-4">
        <h2 className="font-semibold">{t('adjustBalanceTitle')}</h2>
        <Input
          type="number"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          data-testid="admin-ws-balance-amount"
        />
        <Input
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder={t('reason')}
          data-testid="admin-ws-balance-reason"
        />
        <Button
          type="button"
          disabled={reason.trim().length < 3}
          onClick={() => setConfirmBal(true)}
          data-testid="admin-ws-balance-open"
        >
          {t('adjustBalance')}
        </Button>
      </section>

      <ConfirmActionDialog
        open={confirmSub}
        onOpenChange={setConfirmSub}
        title={t('confirmPlanTitle')}
        description={t('confirmPlanBody')}
        confirmLabel={t('confirm')}
        cancelLabel={t('cancel')}
        busy={adjustSub.isPending}
        onConfirm={() => {
          adjustSub.mutate(
            { planCode: planCode || ws.planCode || 'free', reason: reason.trim() },
            {
              onSuccess: () => {
                setConfirmSub(false);
                toast({ title: t('planUpdated') });
              },
              onError: (error) =>
                toast({
                  variant: 'destructive',
                  title: t('errorTitle'),
                  description: getErrorMessage(error),
                }),
            },
          );
        }}
      />

      <ConfirmActionDialog
        open={confirmBal}
        onOpenChange={setConfirmBal}
        title={t('confirmBalanceTitle')}
        description={t('confirmBalanceBody')}
        confirmLabel={t('confirm')}
        cancelLabel={t('cancel')}
        busy={adjustBal.isPending}
        onConfirm={() => {
          adjustBal.mutate(
            { amount: Number(amount), reason: reason.trim(), kind: 'admin_credit' },
            {
              onSuccess: () => {
                setConfirmBal(false);
                toast({ title: t('balanceUpdated') });
              },
              onError: (error) =>
                toast({
                  variant: 'destructive',
                  title: t('errorTitle'),
                  description: getErrorMessage(error),
                }),
            },
          );
        }}
      />
    </div>
  );
}
