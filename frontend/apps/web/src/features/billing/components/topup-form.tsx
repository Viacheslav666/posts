'use client';

import { Button, Input, toast } from '@posts/ui';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useState } from 'react';

import { getErrorMessage } from '@/shared/lib/errors';

import { useCheckoutMutation } from '../hooks/use-billing-queries';
import {
  DEFAULT_TOPUP_RUB,
  formatRubAmount,
  MIN_TOPUP_KOPECKS,
  rublesToKopecks,
  TOPUP_PRESETS_RUB,
} from '../model/money';

type TopupFormProps = {
  workspaceId: string;
  canManage?: boolean;
};

export function TopupForm({ workspaceId, canManage }: TopupFormProps) {
  const t = useTranslations('billing.topup');
  const router = useRouter();
  const checkout = useCheckoutMutation(workspaceId);
  const [amountRub, setAmountRub] = useState(String(DEFAULT_TOPUP_RUB));
  const [offerAccepted, setOfferAccepted] = useState(false);

  if (!canManage) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="billing-topup-readonly">
        {t('viewOnly')}
      </p>
    );
  }

  const parsed = Number.parseFloat(amountRub.replace(',', '.'));
  const amountKopecks = Number.isFinite(parsed) ? rublesToKopecks(parsed) : 0;
  const tooSmall = amountKopecks < MIN_TOPUP_KOPECKS;

  return (
    <section className="space-y-4 rounded-xl border border-border p-4" data-testid="billing-topup">
      <div>
        <h2 className="text-lg font-semibold text-foreground">{t('title')}</h2>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
      </div>

      <div className="flex flex-wrap gap-2" role="group" aria-label={t('presetsAria')}>
        {TOPUP_PRESETS_RUB.map((preset) => (
          <Button
            key={preset}
            type="button"
            size="sm"
            variant={Number(amountRub) === preset ? 'default' : 'outline'}
            data-testid={`billing-topup-preset-${preset}`}
            onClick={() => setAmountRub(String(preset))}
          >
            {formatRubAmount(preset)}
          </Button>
        ))}
      </div>

      <label className="block space-y-1 text-sm">
        <span>{t('amountLabel')}</span>
        <Input
          inputMode="decimal"
          value={amountRub}
          onChange={(event) => setAmountRub(event.target.value)}
          data-testid="billing-topup-amount"
          aria-invalid={tooSmall}
          aria-describedby="billing-topup-hint"
        />
        <span id="billing-topup-hint" className="text-xs text-muted-foreground">
          {t('minHint', { amount: formatRubAmount(MIN_TOPUP_KOPECKS / 100) })}
        </span>
      </label>

      <label className="flex items-start gap-2 text-sm">
        <input
          type="checkbox"
          className="mt-1 h-4 w-4"
          checked={offerAccepted}
          onChange={(event) => setOfferAccepted(event.target.checked)}
          data-testid="billing-topup-offer"
        />
        <span>{t('offer')}</span>
      </label>

      <Button
        type="button"
        disabled={checkout.isPending || tooSmall || !offerAccepted}
        data-testid="billing-topup-submit"
        onClick={() => {
          checkout.mutate(
            { amountKopecks },
            {
              onSuccess: (data) => {
                const url = data.confirmationUrl;
                if (!url) {
                  toast({
                    variant: 'destructive',
                    title: t('noRedirectTitle'),
                    description: t('noRedirectBody'),
                  });
                  return;
                }
                // Same-origin mock return keeps SPA session; external ЮKassa uses full redirect.
                if (url.startsWith('/')) {
                  router.push(url);
                  return;
                }
                window.location.assign(url);
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
        {checkout.isPending ? t('pending') : t('submit')}
      </Button>
    </section>
  );
}
