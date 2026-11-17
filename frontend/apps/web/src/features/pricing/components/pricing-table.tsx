'use client';

import { Badge, Button, cn } from '@posts/ui';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import { useState } from 'react';

import { formatRub, PLANS, priceForPeriod } from '../model/plans';

export function PricingTable() {
  const t = useTranslations('pricing');
  const [yearly, setYearly] = useState(true);

  return (
    <div>
      <div className="flex flex-wrap items-center justify-center gap-3">
        <div
          role="group"
          aria-label={t('periodLabel')}
          className="inline-flex rounded-md border border-border bg-background/70 p-1"
        >
          <button
            type="button"
            className={cn(
              'rounded px-3 py-1.5 text-sm font-medium transition-colors',
              !yearly ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground',
            )}
            aria-pressed={!yearly}
            onClick={() => setYearly(false)}
          >
            {t('monthly')}
          </button>
          <button
            type="button"
            className={cn(
              'rounded px-3 py-1.5 text-sm font-medium transition-colors',
              yearly ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground',
            )}
            aria-pressed={yearly}
            onClick={() => setYearly(true)}
          >
            {t('yearly')}
          </button>
        </div>
        {yearly ? <Badge variant="published">{t('yearlyBadge')}</Badge> : null}
      </div>

      <div className="mt-10 grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        {PLANS.map((plan, index) => {
          const price = priceForPeriod(plan.monthlyPrice, yearly);
          return (
            <article
              key={plan.id}
              className={cn(
                'pricing-card flex flex-col rounded-xl border border-border/80 bg-background/75 p-6 shadow-sm backdrop-blur-sm',
                plan.highlighted && 'border-primary/50 ring-1 ring-primary/30',
              )}
              style={{ animationDelay: `${index * 80}ms` }}
            >
              <div className="flex items-center justify-between gap-2">
                <h2 className="font-display text-2xl font-semibold text-foreground">{t(`plans.${plan.nameKey}.name`)}</h2>
                {plan.highlighted ? <Badge variant="scheduled">{t('popular')}</Badge> : null}
              </div>
              <p className="mt-2 text-sm text-muted-foreground">{t(`plans.${plan.nameKey}.blurb`)}</p>
              <p className="mt-6">
                <span className="font-display text-4xl font-bold tracking-tight text-foreground">
                  {price === 0 ? t('freePrice') : `${formatRub(price)} ₽`}
                </span>
                {price > 0 ? (
                  <span className="ml-1 text-sm text-muted-foreground">
                    {yearly ? t('perYear') : t('perMonth')}
                  </span>
                ) : null}
              </p>
              <ul className="mt-6 space-y-2 text-sm text-foreground">
                <li>{t('limits.channels', { value: plan.channels })}</li>
                <li>{t('limits.seats', { value: plan.seats })}</li>
                <li>{t('limits.posts', { value: plan.posts })}</li>
                <li>{t('limits.ai', { value: plan.aiCredits })}</li>
              </ul>
              {plan.noteKey ? (
                <p className="mt-3 text-xs text-muted-foreground">{t(plan.noteKey)}</p>
              ) : null}
              <div className="mt-auto pt-6">
                <Button asChild className="w-full" variant={plan.highlighted ? 'default' : 'outline'}>
                  <Link href="/register">{plan.id === 'free' ? t('ctaFree') : t('ctaTrial')}</Link>
                </Button>
              </div>
            </article>
          );
        })}
      </div>

      <p className="mt-8 text-center text-sm text-muted-foreground">{t('trialNote')}</p>
    </div>
  );
}
