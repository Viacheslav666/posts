'use client';

import { Button } from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useEffect } from 'react';

import { getErrorMessage } from '@/shared/lib/errors';

type ErrorProps = {
  error: Error & { digest?: string };
  reset: () => void;
};

export default function GlobalError({ error, reset }: ErrorProps) {
  const t = useTranslations('errors');

  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main id="main-content" className="mx-auto flex min-h-[60vh] max-w-lg flex-col justify-center gap-4 px-6 py-16">
      <h1 className="font-display text-3xl font-bold text-foreground">{t('boundaryTitle')}</h1>
      <p className="text-muted-foreground">{t('boundaryDescription')}</p>
      <p className="rounded-md border border-border bg-muted/40 px-3 py-2 text-sm text-muted-foreground">
        {getErrorMessage(error, t('generic'))}
      </p>
      <div>
        <Button onClick={reset}>{t('retry')}</Button>
      </div>
    </main>
  );
}
