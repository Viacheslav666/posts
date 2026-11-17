'use client';

import { Button } from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useEffect } from 'react';

import { getErrorMessage } from '@/shared/lib/errors';

type ErrorProps = {
  error: Error & { digest?: string };
  reset: () => void;
};

export default function WorkspaceError({ error, reset }: ErrorProps) {
  const t = useTranslations('errors');

  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="mx-auto flex max-w-lg flex-col gap-4 py-10">
      <h2 className="text-xl font-semibold text-foreground">{t('boundaryTitle')}</h2>
      <p className="text-sm text-muted-foreground">{getErrorMessage(error, t('generic'))}</p>
      <div>
        <Button onClick={reset}>{t('retry')}</Button>
      </div>
    </div>
  );
}
