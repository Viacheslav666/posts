import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';

import { LandingPageContent } from '@/features/landing/components/landing-page';

// Footer requisites are read per request so container env overrides the image.
export const dynamic = 'force-dynamic';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('meta');
  return {
    title: t('title'),
    description: t('description'),
    openGraph: {
      title: t('title'),
      description: t('description'),
      locale: 'ru_RU',
      type: 'website',
    },
  };
}

export default function LandingPage() {
  return (
    <main id="main-content">
      <LandingPageContent />
    </main>
  );
}
