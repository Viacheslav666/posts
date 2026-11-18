import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';

import { LegalStub } from '@/features/landing/components/legal-stub';
import { getLegalRequisites, hasLegalValue } from '@/shared/config/legal';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('legal.contacts');
  return { title: t('title') };
}

export default async function ContactsPage() {
  const t = await getTranslations('legal');
  const requisites = getLegalRequisites();
  const rows = [
    { label: t('contacts.company'), value: requisites.companyName },
    { label: t('contacts.inn'), value: requisites.inn },
    { label: t('contacts.ogrn'), value: requisites.ogrn },
    { label: t('contacts.address'), value: requisites.address },
    { label: t('contacts.email'), value: requisites.email },
    { label: t('contacts.phone'), value: requisites.phone },
  ];

  return (
    <LegalStub title={t('contacts.title')} body={t('contacts.body')} draftLabel={t('draftBanner')}>
      <dl className="mt-8 space-y-3 text-sm">
        {rows.map((row) => (
          <div key={row.label} className="grid gap-1 sm:grid-cols-[10rem_1fr]">
            <dt className="font-medium text-foreground">{row.label}</dt>
            <dd className="text-muted-foreground">
              {hasLegalValue(row.value) ? row.value : t('contacts.placeholder')}
            </dd>
          </div>
        ))}
      </dl>
    </LegalStub>
  );
}
