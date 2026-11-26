import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';

import { PublicOffer } from '@/features/landing/components/public-offer';

// Requisites are read per request so container env overrides the image.
export const dynamic = 'force-dynamic';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('legal.offer');
  return { title: t('title') };
}

export default function OfferPage() {
  return <PublicOffer />;
}
