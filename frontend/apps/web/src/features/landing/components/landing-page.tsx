import { Button, NetworkIcon } from '@posts/ui';
import { getTranslations } from 'next-intl/server';
import Link from 'next/link';
import type { ReactNode } from 'react';

import { PricingTable } from '@/features/pricing/components/pricing-table';

import { PublicFooter } from './public-footer';

const FEATURE_KEYS = [
  'scheduling',
  'stories',
  'grabber',
  'ord',
  'calendar',
  'team',
  'analytics',
  'reliability',
] as const;

const HOW_STEPS = ['1', '2', '3'] as const;
const FAQ_ITEMS = ['1', '2', '3', '4', '5'] as const;

export async function LandingPageContent() {
  const t = await getTranslations('landing');
  const common = await getTranslations('common');

  return (
    <>
      <section
        className="relative isolate overflow-hidden"
        data-testid="landing-hero"
        aria-labelledby="landing-hero-heading"
      >
        <div className="pointer-events-none absolute inset-0 -z-10" aria-hidden>
          <div className="landing-orb landing-orb-a absolute -left-24 top-10 h-72 w-72 rounded-full bg-primary/20 blur-3xl" />
          <div className="landing-orb landing-orb-b absolute -right-16 top-32 h-80 w-80 rounded-full bg-info/15 blur-3xl" />
          <div className="absolute inset-x-0 bottom-0 h-40 bg-gradient-to-t from-background to-transparent" />
          <div
            className="absolute inset-0 opacity-[0.07]"
            style={{
              backgroundImage:
                'linear-gradient(hsl(var(--foreground) / 0.15) 1px, transparent 1px), linear-gradient(90deg, hsl(var(--foreground) / 0.15) 1px, transparent 1px)',
              backgroundSize: '48px 48px',
              maskImage: 'radial-gradient(ellipse at center, black 20%, transparent 75%)',
            }}
          />
        </div>

        <div className="mx-auto flex min-h-[100svh] w-full max-w-6xl flex-col justify-center px-4 pb-20 pt-28 sm:px-6 lg:px-8">
          <p className="landing-fade font-display text-5xl font-bold tracking-tight text-foreground sm:text-6xl md:text-7xl">
            {common('brand')}
          </p>
          <h1
            id="landing-hero-heading"
            className="landing-fade landing-fade-delay-1 mt-6 max-w-3xl text-2xl font-medium leading-snug text-foreground sm:text-3xl md:text-4xl"
          >
            {t('headline')}
          </h1>
          <p className="landing-fade landing-fade-delay-2 mt-4 max-w-2xl text-base text-muted-foreground sm:text-lg">
            {t('subtitle')}
          </p>
          <div className="landing-fade landing-fade-delay-3 mt-8 flex flex-wrap gap-3">
            <Button asChild size="lg">
              <Link href="/register">{t('ctaPrimary')}</Link>
            </Button>
            <Button asChild size="lg" variant="outline">
              <Link href="/pricing">{t('ctaSecondary')}</Link>
            </Button>
          </div>
        </div>
      </section>

      <Section
        id="networks"
        testId="landing-networks"
        title={t('networksLabel')}
        className="border-y border-border/60 bg-muted/20"
      >
        <div className="flex flex-wrap items-center gap-6">
          <NetworkIcon network="vk" className="size-8" />
          <NetworkIcon network="telegram" className="size-8" />
          <NetworkIcon network="ok" className="size-8" />
          <NetworkIcon network="max" className="size-8" />
        </div>
        <p className="mt-4 text-sm text-muted-foreground">{t('networksNote')}</p>
      </Section>

      <Section
        id="features"
        testId="landing-features"
        title={t('features.title')}
        subtitle={t('features.subtitle')}
      >
        <ul className="grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURE_KEYS.map((key) => (
            <li key={key} className="space-y-2">
              <h3 className="font-display text-lg font-semibold text-foreground">
                {t(`features.items.${key}.title`)}
              </h3>
              <p className="text-sm leading-relaxed text-muted-foreground">
                {t(`features.items.${key}.body`)}
              </p>
            </li>
          ))}
        </ul>
      </Section>

      <Section
        id="how"
        testId="landing-how"
        title={t('how.title')}
        subtitle={t('how.subtitle')}
        className="bg-muted/15"
      >
        <ol className="grid gap-8 md:grid-cols-3">
          {HOW_STEPS.map((step) => (
            <li key={step} className="space-y-3">
              <span className="font-display text-4xl font-bold text-primary/80">{step}</span>
              <h3 className="text-lg font-semibold text-foreground">{t(`how.steps.${step}.title`)}</h3>
              <p className="text-sm text-muted-foreground">{t(`how.steps.${step}.body`)}</p>
            </li>
          ))}
        </ol>
      </Section>

      <Section
        id="pricing"
        testId="landing-pricing"
        title={t('pricing.title')}
        subtitle={t('pricing.subtitle')}
      >
        <p className="mb-8 max-w-2xl text-sm text-muted-foreground">{t('pricing.balanceNote')}</p>
        <PricingTable />
        <div className="mt-8">
          <Button asChild variant="outline">
            <Link href="/pricing">{t('pricing.cta')}</Link>
          </Button>
        </div>
      </Section>

      <Section id="faq" testId="landing-faq" title={t('faq.title')} className="bg-muted/15">
        <div className="mx-auto max-w-3xl space-y-3">
          {FAQ_ITEMS.map((id) => (
            <details
              key={id}
              className="group border-b border-border/70 py-3 open:pb-4"
            >
              <summary className="cursor-pointer list-none text-base font-medium text-foreground marker:content-none [&::-webkit-details-marker]:hidden">
                <span className="flex items-center justify-between gap-4">
                  {t(`faq.items.${id}.q`)}
                  <span className="text-muted-foreground transition group-open:rotate-45" aria-hidden>
                    +
                  </span>
                </span>
              </summary>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                {t(`faq.items.${id}.a`)}
              </p>
            </details>
          ))}
        </div>
      </Section>

      <section
        className="border-y border-border/60"
        data-testid="landing-final-cta"
        aria-labelledby="landing-final-cta-heading"
      >
        <div className="mx-auto flex w-full max-w-6xl flex-col items-start gap-6 px-4 py-20 sm:px-6 lg:flex-row lg:items-center lg:justify-between lg:px-8">
          <div className="max-w-xl space-y-3">
            <h2 id="landing-final-cta-heading" className="font-display text-3xl font-bold text-foreground">
              {t('finalCta.title')}
            </h2>
            <p className="text-muted-foreground">{t('finalCta.subtitle')}</p>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <Link href="/register">{t('finalCta.primary')}</Link>
            </Button>
            <Button asChild size="lg" variant="outline">
              <Link href="/login">{t('finalCta.secondary')}</Link>
            </Button>
          </div>
        </div>
      </section>

      <PublicFooter testId="landing-footer" />
    </>
  );
}

function Section({
  id,
  title,
  subtitle,
  children,
  className = '',
  testId,
}: {
  id: string;
  title: string;
  subtitle?: string;
  children: ReactNode;
  className?: string;
  testId: string;
}) {
  return (
    <section id={id} className={className} data-testid={testId} aria-labelledby={`${id}-heading`}>
      <div className="mx-auto w-full max-w-6xl px-4 py-16 sm:px-6 lg:px-8">
        <h2 id={`${id}-heading`} className="font-display text-3xl font-bold tracking-tight text-foreground">
          {title}
        </h2>
        {subtitle ? <p className="mt-3 max-w-2xl text-muted-foreground">{subtitle}</p> : null}
        <div className="mt-10">{children}</div>
      </div>
    </section>
  );
}
