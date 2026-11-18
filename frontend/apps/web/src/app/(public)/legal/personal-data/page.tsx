import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';

import { LegalStub } from '@/features/landing/components/legal-stub';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('legal.personalData');
  return { title: t('title') };
}

export default async function PersonalDataPage() {
  const t = await getTranslations('legal');
  return (
    <LegalStub
      title={t('personalData.title')}
      body={t('personalData.body')}
      draftLabel={t('draftBanner')}
    />
  );
}
