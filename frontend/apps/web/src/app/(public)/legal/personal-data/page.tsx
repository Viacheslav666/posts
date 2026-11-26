import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';

import { PersonalDataConsent } from '@/features/landing/components/personal-data-consent';

// Operator requisites are read per request so container env overrides the image.
export const dynamic = 'force-dynamic';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('legal.personalData');
  return { title: t('title') };
}

export default function PersonalDataPage() {
  return <PersonalDataConsent />;
}
