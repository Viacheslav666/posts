'use client';

import { Button, NetworkIcon } from '@posts/ui';
import { useTranslations } from 'next-intl';
import Link from 'next/link';

export function LandingHero() {
  const t = useTranslations('landing');
  const common = useTranslations('common');

  return (
    <section className="relative isolate overflow-hidden">
      <div className="pointer-events-none absolute inset-0 -z-10">
        <div className="landing-orb landing-orb-a absolute -left-24 top-10 h-72 w-72 rounded-full bg-primary/20 blur-3xl" />
        <div className="landing-orb landing-orb-b absolute -right-16 top-32 h-80 w-80 rounded-full bg-info/15 blur-3xl" />
        <div className="absolute inset-x-0 bottom-0 h-40 bg-gradient-to-t from-background to-transparent" />
      </div>

      <div className="mx-auto flex min-h-[100svh] w-full max-w-6xl flex-col justify-center px-4 pb-20 pt-28 sm:px-6 lg:px-8">
        <p className="landing-fade font-display text-5xl font-bold tracking-tight text-foreground sm:text-6xl md:text-7xl">
          {common('brand')}
        </p>
        <h1 className="landing-fade landing-fade-delay-1 mt-6 max-w-3xl text-2xl font-medium leading-snug text-foreground sm:text-3xl md:text-4xl">
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
        <div className="landing-fade landing-fade-delay-4 mt-14 flex flex-wrap items-center gap-4 text-sm text-muted-foreground">
          <span>{t('networks')}</span>
          <div className="flex items-center gap-3">
            <NetworkIcon network="vk" className="size-6" />
            <NetworkIcon network="telegram" className="size-6" />
            <NetworkIcon network="ok" className="size-6" />
            <NetworkIcon network="max" className="size-6" />
          </div>
        </div>
      </div>
    </section>
  );
}
