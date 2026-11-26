import { cn } from '@posts/ui';
import { getTranslations } from 'next-intl/server';

import {
  legalRequisiteRows,
  type LegalField,
  type LegalRequisiteRow,
  type LegalRequisites,
} from '@/shared/config/legal';
import { readRequestLegalRequisites } from '@/shared/config/legal-request';

export async function loadLegalRequisiteRows(): Promise<{
  requisites: LegalRequisites;
  rows: LegalRequisiteRow[];
}> {
  const t = await getTranslations('legal');
  const requisites = await readRequestLegalRequisites();
  return {
    requisites,
    rows: legalRequisiteRows(requisites, {
      companyName: t('contacts.company'),
      inn: t('contacts.inn'),
      taxStatus: t('contacts.taxStatus'),
      ogrn: t('contacts.ogrn'),
      address: t('contacts.address'),
      email: t('contacts.email'),
      phone: t('contacts.phone'),
    }),
  };
}

function LegalValue({ field, value }: { field: LegalField; value: string }) {
  if (field === 'email' && /^[^\s@]+@[^\s@]+$/.test(value)) {
    return (
      <a href={`mailto:${value}`} className="underline underline-offset-2 hover:text-foreground">
        {value}
      </a>
    );
  }
  if (field === 'phone') {
    const tel = value.replace(/[^\d+]/g, '');
    if (tel.length > 0) {
      return (
        <a href={`tel:${tel}`} className="underline underline-offset-2 hover:text-foreground">
          {value}
        </a>
      );
    }
  }
  return value;
}

export function LegalRequisitesView({
  rows,
  variant = 'definition',
  className,
}: {
  rows: LegalRequisiteRow[];
  variant?: 'definition' | 'compact';
  className?: string;
}) {
  if (rows.length === 0) {
    return null;
  }

  if (variant === 'compact') {
    return (
      <ul
        className={cn('mt-2 space-y-1 text-xs text-muted-foreground', className)}
        data-testid="legal-requisites"
      >
        {rows.map((row) => (
          <li key={row.field} data-testid={`legal-row-${row.field}`}>
            {row.label}: <LegalValue field={row.field} value={row.value} />
          </li>
        ))}
      </ul>
    );
  }

  return (
    <dl className={cn('mt-8 space-y-3 text-sm', className)} data-testid="legal-requisites">
      {rows.map((row) => (
        <div key={row.field} className="grid gap-1 sm:grid-cols-[10rem_1fr]" data-testid={`legal-row-${row.field}`}>
          <dt className="font-medium text-foreground">{row.label}</dt>
          <dd className="text-muted-foreground">
            <LegalValue field={row.field} value={row.value} />
          </dd>
        </div>
      ))}
    </dl>
  );
}
