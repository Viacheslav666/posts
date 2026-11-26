import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';

import { PrivacyPolicy } from '@/features/landing/components/privacy-policy';

// Operator requisites are read per request so container env overrides the image.
export const dynamic = 'force-dynamic';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('legal.privacy');
  return { title: t('title') };
}

export default function PrivacyPage() {
  return <PrivacyPolicy />;
}
