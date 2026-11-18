'use client';

import { EmptyState, Skeleton } from '@posts/ui';
import { ShieldOff } from 'lucide-react';
import { useTranslations } from 'next-intl';
import type { ReactNode } from 'react';

import { useIsPlatformAdmin } from '../hooks/use-admin-queries';

type AdminGuardProps = {
  children: ReactNode;
};

export function AdminGuard({ children }: AdminGuardProps) {
  const t = useTranslations('admin');
  const { isPlatformAdmin, isLoading, isError, isImpersonating } = useIsPlatformAdmin();

  if (isLoading) {
    return (
      <div className="space-y-3 p-6" data-testid="admin-loading">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }

  if (isError) {
    return (
      <EmptyState
        title={t('errorTitle')}
        description={t('errorBody')}
        data-testid="admin-error"
      />
    );
  }

  if (!isPlatformAdmin || isImpersonating) {
    return (
      <EmptyState
        icon={<ShieldOff aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
        data-testid="admin-forbidden"
      />
    );
  }

  return <>{children}</>;
}
