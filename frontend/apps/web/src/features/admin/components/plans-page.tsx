'use client';

import { EmptyState, Skeleton } from '@posts/ui';
import { useTranslations } from 'next-intl';

import { useAdminPlans, useIsPlatformAdmin } from '../hooks/use-admin-queries';

export function AdminPlansPage() {
  const t = useTranslations('admin.plans');
  const { isPlatformAdmin } = useIsPlatformAdmin();
  const query = useAdminPlans(isPlatformAdmin);

  return (
    <div className="space-y-4" data-testid="admin-plans-page">
      <div>
        <h1 className="font-display text-3xl font-semibold tracking-tight">{t('title')}</h1>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
      </div>

      {query.isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : query.isError ? (
        <EmptyState title={t('errorTitle')} description={t('errorBody')} />
      ) : !query.data?.length ? (
        <EmptyState title={t('emptyTitle')} description={t('emptyBody')} />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border" data-testid="admin-plans-table">
          <table className="w-full min-w-[36rem] text-left text-sm">
            <thead className="border-b border-border bg-muted/40 text-muted-foreground">
              <tr>
                <th className="px-3 py-2 font-medium">{t('columns.code')}</th>
                <th className="px-3 py-2 font-medium">{t('columns.name')}</th>
                <th className="px-3 py-2 font-medium">{t('columns.month')}</th>
                <th className="px-3 py-2 font-medium">{t('columns.year')}</th>
              </tr>
            </thead>
            <tbody>
              {query.data.map((plan) => (
                <tr key={plan.id ?? plan.code} className="border-b border-border/70 last:border-0">
                  <td className="px-3 py-2 font-mono">{plan.code}</td>
                  <td className="px-3 py-2">{plan.name}</td>
                  <td className="px-3 py-2">{plan.priceMonth}</td>
                  <td className="px-3 py-2">{plan.priceYear}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
