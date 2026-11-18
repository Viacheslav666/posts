import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';

import { LegalStub } from '@/features/landing/components/legal-stub';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('legal.offer');
  return { title: t('title') };
}

export default async function OfferPage() {
  const t = await getTranslations('legal');
  return (
    <LegalStub title={t('offer.title')} body={t('offer.body')} draftLabel={t('draftBanner')} />
  );
}
