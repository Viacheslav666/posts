'use client';

import { EmptyState, Input, Skeleton, Button } from '@posts/ui';
import { Building2, Search } from 'lucide-react';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import { useState } from 'react';

import { useAdminWorkspaces, useIsPlatformAdmin } from '../hooks/use-admin-queries';

export function AdminWorkspacesPage() {
  const t = useTranslations('admin.workspaces');
  const { isPlatformAdmin } = useIsPlatformAdmin();
  const [q, setQ] = useState('');
  const [submitted, setSubmitted] = useState('');
  const [planCode, setPlanCode] = useState('');
  const query = useAdminWorkspaces(
    {
      q: submitted || undefined,
      planCode: planCode || undefined,
    },
    isPlatformAdmin,
  );

  return (
    <div className="space-y-4" data-testid="admin-workspaces-page">
      <div>
        <h1 className="font-display text-3xl font-semibold tracking-tight">{t('title')}</h1>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
      </div>

      <form
        className="flex flex-wrap gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          setSubmitted(q.trim());
        }}
      >
        <div className="relative min-w-[14rem] flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder={t('searchPlaceholder')}
            className="pl-9"
            data-testid="admin-workspaces-search"
            aria-label={t('searchPlaceholder')}
          />
        </div>
        <Input
          value={planCode}
          onChange={(e) => setPlanCode(e.target.value)}
          placeholder={t('planFilter')}
          className="w-36"
          data-testid="admin-workspaces-plan-filter"
          aria-label={t('planFilter')}
        />
        <Button type="submit">{t('search')}</Button>
      </form>

      {query.isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : query.isError ? (
        <EmptyState title={t('errorTitle')} description={t('errorBody')} />
      ) : !query.data?.items?.length ? (
        <EmptyState
          icon={<Building2 aria-hidden />}
          title={t('emptyTitle')}
          description={t('emptyBody')}
          data-testid="admin-workspaces-empty"
        />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border" data-testid="admin-workspaces-table">
          <table className="w-full min-w-[40rem] text-left text-sm">
            <thead className="border-b border-border bg-muted/40 text-muted-foreground">
              <tr>
                <th className="px-3 py-2 font-medium">{t('columns.name')}</th>
                <th className="px-3 py-2 font-medium">{t('columns.owner')}</th>
                <th className="px-3 py-2 font-medium">{t('columns.plan')}</th>
                <th className="px-3 py-2 font-medium">{t('columns.status')}</th>
              </tr>
            </thead>
            <tbody>
              {query.data.items.map((ws) => (
                <tr key={ws.id} className="border-b border-border/70 last:border-0">
                  <td className="px-3 py-2">
                    <Link
                      href={`/admin/workspaces/${ws.id}`}
                      className="font-medium text-primary underline-offset-2 hover:underline"
                    >
                      {ws.name}
                    </Link>
                  </td>
                  <td className="px-3 py-2">{ws.ownerEmail}</td>
                  <td className="px-3 py-2">{ws.planCode}</td>
                  <td className="px-3 py-2">{ws.billingStatus}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
