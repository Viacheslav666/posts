'use client';

import { Button, Skeleton, toast } from '@posts/ui';
import { useTranslations } from 'next-intl';

import { getErrorMessage } from '@/shared/lib/errors';

import { useBillingPlans, useChangePlanMutation } from '../hooks/use-billing-queries';
import { formatKopecks } from '../model/money';

type PlanPickerProps = {
  workspaceId: string;
  currentPlanCode?: string;
  canManage?: boolean;
};

export function PlanPicker({ workspaceId, currentPlanCode, canManage }: PlanPickerProps) {
  const t = useTranslations('billing.plans');
  const plansQuery = useBillingPlans(true);
  const changePlan = useChangePlanMutation(workspaceId);

  const plans = (plansQuery.data ?? []).filter(
    (plan) => plan.billingModel === 'balance' || (plan.code?.startsWith('daily_') ?? false) || plan.code === 'free',
  );

  if (plansQuery.isLoading) {
    return (
      <section className="space-y-3 rounded-xl border border-border p-4" data-testid="billing-plans-loading">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-16 w-full" />
      </section>
    );
  }

  return (
    <section className="space-y-4 rounded-xl border border-border p-4" data-testid="billing-plans">
      <div>
        <h2 className="text-lg font-semibold text-foreground">{t('title')}</h2>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
      </div>

      <ul className="grid gap-3 sm:grid-cols-2">
        {plans.map((plan) => {
          const active = plan.code === currentPlanCode;
          return (
            <li
              key={plan.id ?? plan.code}
              className={`space-y-2 rounded-lg border p-3 ${active ? 'border-primary bg-primary/5' : 'border-border'}`}
              data-testid={`billing-plan-${plan.code}`}
            >
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className="font-medium text-foreground">{plan.name}</p>
                  <p className="text-xs text-muted-foreground">{plan.code}</p>
                </div>
                {active ? (
                  <span className="text-xs font-medium text-primary">{t('current')}</span>
                ) : null}
              </div>
              <p className="text-sm text-foreground">
                {(plan.priceDayKopecks ?? 0) > 0
                  ? t('perDay', { amount: formatKopecks(plan.priceDayKopecks ?? 0) })
                  : t('free')}
              </p>
              {canManage && !active && plan.code ? (
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={changePlan.isPending}
                  data-testid={`billing-plan-select-${plan.code}`}
                  onClick={() => {
                    changePlan.mutate(plan.code!, {
                      onSuccess: () => toast({ title: t('changed') }),
                      onError: (error) =>
                        toast({
                          variant: 'destructive',
                          title: t('errorTitle'),
                          description: getErrorMessage(error),
                        }),
                    });
                  }}
                >
                  {t('select')}
                </Button>
              ) : null}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
