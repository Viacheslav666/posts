'use client';

import { Badge, EmptyState, Skeleton } from '@posts/ui';
import { LayoutList } from 'lucide-react';
import { useTranslations } from 'next-intl';
import Link from 'next/link';

import { useAdminQueues, useIsPlatformAdmin } from '../hooks/use-admin-queries';

export function AdminQueuesPage() {
  const t = useTranslations('admin.queues');
  const { isPlatformAdmin } = useIsPlatformAdmin();
  const query = useAdminQueues(isPlatformAdmin);

  return (
    <div className="space-y-4" data-testid="admin-queues-page">
      <div>
        <h1 className="font-display text-3xl font-semibold tracking-tight">{t('title')}</h1>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
      </div>

      {query.isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : query.isError ? (
        <EmptyState title={t('errorTitle')} description={t('errorBody')} />
      ) : !query.data?.length ? (
        <EmptyState icon={<LayoutList aria-hidden />} title={t('emptyTitle')} description={t('emptyBody')} />
      ) : (
        <ul className="space-y-2" role="list" data-testid="admin-queues-list">
          {query.data.map((queue) => (
            <li
              key={queue.name}
              className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border bg-card/40 px-4 py-3"
            >
              <div>
                <Link
                  href={`/admin/queues/${encodeURIComponent(queue.name ?? '')}`}
                  className="font-medium text-primary underline-offset-2 hover:underline"
                  data-testid={`admin-queue-link-${queue.name}`}
                >
                  {queue.name}
                </Link>
                <p className="text-xs text-muted-foreground">
                  {t('messages', { count: queue.messageCount ?? 0 })} ·{' '}
                  {t('consumers', { count: queue.consumerCount ?? 0 })}
                </p>
              </div>
              {queue.dlq ? <Badge variant="failed">{t('dlq')}</Badge> : <Badge variant="secondary">{t('main')}</Badge>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
