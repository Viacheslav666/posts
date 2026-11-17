'use client';

import { useTranslations } from 'next-intl';

export function AuthDivider() {
  const t = useTranslations('auth');

  return (
    <div className="relative my-6">
      <div className="absolute inset-0 flex items-center" aria-hidden>
        <div className="w-full border-t border-border" />
      </div>
      <div className="relative flex justify-center text-xs uppercase tracking-wide">
        <span className="bg-background px-3 text-muted-foreground">{t('or')}</span>
      </div>
    </div>
  );
}
