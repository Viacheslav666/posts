'use client';

import { EmptyState, Input, Skeleton, Button } from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useState } from 'react';

import { useAdminAudit, useIsPlatformAdmin } from '../hooks/use-admin-queries';

export function AdminAuditPage() {
  const t = useTranslations('admin.audit');
  const { isPlatformAdmin } = useIsPlatformAdmin();
  const [action, setAction] = useState('');
  const [submitted, setSubmitted] = useState('');
  const query = useAdminAudit({ action: submitted || undefined }, isPlatformAdmin);

  return (
    <div className="space-y-4" data-testid="admin-audit-page">
      <div>
        <h1 className="font-display text-3xl font-semibold tracking-tight">{t('title')}</h1>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
      </div>

      <form
        className="flex flex-wrap gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          setSubmitted(action.trim());
        }}
      >
        <Input
          value={action}
          onChange={(e) => setAction(e.target.value)}
          placeholder={t('actionFilter')}
          className="min-w-[16rem] flex-1"
          data-testid="admin-audit-action-filter"
          aria-label={t('actionFilter')}
        />
        <Button type="submit">{t('filter')}</Button>
      </form>

      {query.isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : query.isError ? (
        <EmptyState title={t('errorTitle')} description={t('errorBody')} />
      ) : !query.data?.items?.length ? (
        <EmptyState title={t('emptyTitle')} description={t('emptyBody')} data-testid="admin-audit-empty" />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border" data-testid="admin-audit-table">
          <table className="w-full min-w-[48rem] text-left text-sm">
            <thead className="border-b border-border bg-muted/40 text-muted-foreground">
              <tr>
                <th className="px-3 py-2 font-medium">{t('columns.when')}</th>
                <th className="px-3 py-2 font-medium">{t('columns.actor')}</th>
                <th className="px-3 py-2 font-medium">{t('columns.action')}</th>
                <th className="px-3 py-2 font-medium">{t('columns.target')}</th>
                <th className="px-3 py-2 font-medium">{t('columns.reason')}</th>
              </tr>
            </thead>
            <tbody>
              {query.data.items.map((entry) => (
                <tr key={entry.id} className="border-b border-border/70 last:border-0">
                  <td className="px-3 py-2 whitespace-nowrap">{entry.createdAt}</td>
                  <td className="px-3 py-2">{entry.actorEmail}</td>
                  <td className="px-3 py-2 font-mono text-xs">{entry.action}</td>
                  <td className="px-3 py-2 text-xs">
                    {entry.targetType}:{entry.targetId}
                  </td>
                  <td className="px-3 py-2">{entry.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
