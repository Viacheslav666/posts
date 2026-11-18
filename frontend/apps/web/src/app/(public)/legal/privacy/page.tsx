import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';

import { LegalStub } from '@/features/landing/components/legal-stub';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('legal.privacy');
  return { title: t('title') };
}

export default async function PrivacyPage() {
  const t = await getTranslations('legal');
  return (
    <LegalStub title={t('privacy.title')} body={t('privacy.body')} draftLabel={t('draftBanner')} />
  );
}
