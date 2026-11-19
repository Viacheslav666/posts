'use client';

import { Badge, cn } from '@posts/ui';
import { useTranslations } from 'next-intl';

export type ReasonChipsProps = {
  codes: string[];
  className?: string;
  /** i18n namespace under automations.common.reasons (default). */
  namespace?: string;
};

export function ReasonChips({ codes, className, namespace = 'automations.common.reasons' }: ReasonChipsProps) {
  const t = useTranslations(namespace);

  if (codes.length === 0) {
    return null;
  }

  return (
    <ul
      className={cn('flex flex-wrap gap-1.5', className)}
      data-testid="reason-chips"
      aria-label={t('label')}
    >
      {codes.map((code) => {
        let label = code;
        try {
          label = t(code as never);
        } catch {
          label = code;
        }
        return (
          <li key={code}>
            <Badge variant="outline" data-reason-code={code}>
              {label}
            </Badge>
          </li>
        );
      })}
    </ul>
  );
}
