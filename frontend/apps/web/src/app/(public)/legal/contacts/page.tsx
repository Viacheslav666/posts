import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';

import { LegalRequisitesView, loadLegalRequisiteRows } from '@/features/landing/components/legal-requisites';
import { LegalStub } from '@/features/landing/components/legal-stub';

// Requisites are read per request so container env overrides the image.
export const dynamic = 'force-dynamic';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('legal.contacts');
  return { title: t('title') };
}

export default async function ContactsPage() {
  const t = await getTranslations('legal');
  const { rows } = await loadLegalRequisiteRows();

  return (
    <LegalStub title={t('contacts.title')} body={t('contacts.body')}>
      <LegalRequisitesView rows={rows} />
    </LegalStub>
  );
}
