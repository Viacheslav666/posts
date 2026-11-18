'use client';

import { Badge, Button, EmptyState, Input, Skeleton } from '@posts/ui';
import { Search, Users } from 'lucide-react';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import { useState } from 'react';

import { useAdminUsers, useIsPlatformAdmin } from '../hooks/use-admin-queries';

export function AdminUsersPage() {
  const t = useTranslations('admin.users');
  const { isPlatformAdmin } = useIsPlatformAdmin();
  const [q, setQ] = useState('');
  const [submitted, setSubmitted] = useState('');
  const query = useAdminUsers({ q: submitted || undefined }, isPlatformAdmin);

  return (
    <div className="space-y-4" data-testid="admin-users-page">
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
        <div className="relative min-w-[16rem] flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder={t('searchPlaceholder')}
            className="pl-9"
            data-testid="admin-users-search"
            aria-label={t('searchPlaceholder')}
          />
        </div>
        <Button type="submit" data-testid="admin-users-search-submit">
          {t('search')}
        </Button>
      </form>

      {query.isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : query.isError ? (
        <EmptyState title={t('errorTitle')} description={t('errorBody')} data-testid="admin-users-error" />
      ) : !query.data?.items?.length ? (
        <EmptyState
          icon={<Users aria-hidden />}
          title={t('emptyTitle')}
          description={t('emptyBody')}
          data-testid="admin-users-empty"
        />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border" data-testid="admin-users-table">
          <table className="w-full min-w-[40rem] text-left text-sm">
            <thead className="border-b border-border bg-muted/40 text-muted-foreground">
              <tr>
                <th className="px-3 py-2 font-medium">{t('columns.email')}</th>
                <th className="px-3 py-2 font-medium">{t('columns.name')}</th>
                <th className="px-3 py-2 font-medium">{t('columns.workspaces')}</th>
                <th className="px-3 py-2 font-medium">{t('columns.flags')}</th>
              </tr>
            </thead>
            <tbody>
              {query.data.items.map((user) => (
                <tr key={user.id} className="border-b border-border/70 last:border-0">
                  <td className="px-3 py-2">
                    <Link
                      href={`/admin/users/${user.id}`}
                      className="font-medium text-primary underline-offset-2 hover:underline"
                      data-testid={`admin-user-link-${user.id}`}
                    >
                      {user.email}
                    </Link>
                  </td>
                  <td className="px-3 py-2">{user.name}</td>
                  <td className="px-3 py-2">{user.workspaceCount ?? 0}</td>
                  <td className="px-3 py-2">
                    {user.platformAdmin ? <Badge variant="secondary">{t('platformAdmin')}</Badge> : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
