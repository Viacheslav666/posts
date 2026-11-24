'use client';

import { Button, Input, toast } from '@posts/ui';
import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';

import { getErrorMessage } from '@/shared/lib/errors';

import { useAutopay, useUpdateAutopayMutation } from '../hooks/use-billing-queries';
import { formatRubAmount, kopecksToRubles, MIN_TOPUP_KOPECKS, rublesToKopecks } from '../model/money';

type AutopayFormProps = {
  workspaceId: string;
  canManage?: boolean;
};

function Switch({
  checked,
  onCheckedChange,
  label,
  disabled,
  id,
}: {
  checked: boolean;
  onCheckedChange: (next: boolean) => void;
  label: string;
  disabled?: boolean;
  id: string;
}) {
  return (
    <button
      type="button"
      id={id}
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      data-testid="billing-autopay-switch"
      onClick={() => onCheckedChange(!checked)}
      className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50 ${
        checked ? 'border-primary bg-primary' : 'border-border bg-muted'
      }`}
    >
      <span
        className={`inline-block h-4 w-4 transform rounded-full bg-background transition-transform ${
          checked ? 'translate-x-6' : 'translate-x-1'
        }`}
      />
    </button>
  );
}

export function AutopayForm({ workspaceId, canManage }: AutopayFormProps) {
  const t = useTranslations('billing.autopay');
  const query = useAutopay(workspaceId, Boolean(canManage) || true);
  const mutation = useUpdateAutopayMutation(workspaceId);

  const [enabled, setEnabled] = useState(false);
  const [thresholdRub, setThresholdRub] = useState('100');
  const [topupRub, setTopupRub] = useState('500');

  useEffect(() => {
    if (!query.data) {
      return;
    }
    setEnabled(Boolean(query.data.enabled));
    setThresholdRub(String(kopecksToRubles(query.data.thresholdKopecks ?? 10_000)));
    setTopupRub(String(kopecksToRubles(query.data.topupAmountKopecks ?? 50_000)));
  }, [query.data]);

  if (!canManage) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="billing-autopay-readonly">
        {t('viewOnly')}
      </p>
    );
  }

  const cardMask = query.data?.paymentMethod?.cardMask;
  const hasMethod = Boolean(query.data?.paymentMethod?.providerPaymentMethodId);

  return (
    <section className="space-y-4 rounded-xl border border-border p-4" data-testid="billing-autopay">
      <div>
        <h2 className="text-lg font-semibold text-foreground">{t('title')}</h2>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
      </div>

      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-sm font-medium text-foreground">{t('enabled')}</p>
          <p className="text-xs text-muted-foreground">
            {hasMethod ? t('card', { mask: cardMask ?? '-' }) : t('noCard')}
          </p>
        </div>
        <Switch
          id="billing-autopay-enabled"
          label={t('enabled')}
          checked={enabled}
          disabled={!hasMethod && !enabled}
          onCheckedChange={setEnabled}
        />
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="space-y-1 text-sm">
          <span>{t('threshold')}</span>
          <Input
            inputMode="decimal"
            value={thresholdRub}
            onChange={(event) => setThresholdRub(event.target.value)}
            data-testid="billing-autopay-threshold"
          />
        </label>
        <label className="space-y-1 text-sm">
          <span>{t('topupAmount')}</span>
          <Input
            inputMode="decimal"
            value={topupRub}
            onChange={(event) => setTopupRub(event.target.value)}
            data-testid="billing-autopay-topup"
          />
          <span className="text-xs text-muted-foreground">
            {t('minHint', { amount: formatRubAmount(MIN_TOPUP_KOPECKS / 100) })}
          </span>
        </label>
      </div>

      <Button
        type="button"
        disabled={mutation.isPending}
        data-testid="billing-autopay-save"
        onClick={() => {
          const thresholdKopecks = rublesToKopecks(Number.parseFloat(thresholdRub.replace(',', '.')) || 0);
          const topupAmountKopecks = rublesToKopecks(Number.parseFloat(topupRub.replace(',', '.')) || 0);
          mutation.mutate(
            { enabled, thresholdKopecks, topupAmountKopecks },
            {
              onSuccess: () => {
                toast({ title: t('saved') });
              },
              onError: (error) => {
                toast({
                  variant: 'destructive',
                  title: t('errorTitle'),
                  description: getErrorMessage(error),
                });
              },
            },
          );
        }}
      >
        {mutation.isPending ? t('pending') : t('save')}
      </Button>
    </section>
  );
}
