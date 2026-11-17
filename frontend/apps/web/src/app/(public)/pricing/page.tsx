import { getTranslations } from 'next-intl/server';

import { PricingTable } from '@/features/pricing/components/pricing-table';

export default async function PricingPage() {
  const t = await getTranslations('pricing');
  const common = await getTranslations('common');

  return (
    <main id="main-content" className="mx-auto w-full max-w-6xl px-4 pb-20 pt-24 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-2xl text-center">
        <p className="font-display text-4xl font-bold tracking-tight text-foreground sm:text-5xl">
          {common('brand')}
        </p>
        <h1 className="mt-4 text-2xl font-semibold text-foreground sm:text-3xl">{t('title')}</h1>
        <p className="mt-3 text-muted-foreground">{t('subtitle')}</p>
      </div>
      <div className="mt-12">
        <PricingTable />
      </div>
    </main>
  );
}
