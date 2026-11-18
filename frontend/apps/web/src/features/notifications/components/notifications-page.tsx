'use client';

import type { NotificationPage } from '@posts/api-client';
import { Button, EmptyState, Skeleton, Tabs, TabsList, TabsTrigger } from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BellOff } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';

import { listNotifications, markNotificationsRead } from '../api/notifications-api';
import { useNotificationUpdates } from '../hooks/use-notification-updates';
import { NotificationItem } from './notification-item';

type NotificationsPageProps = {
  workspaceId: string;
};

export function NotificationsPage({ workspaceId }: NotificationsPageProps) {
  const t = useTranslations('notifications');
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<'all' | 'unread'>('all');
  useNotificationUpdates({ workspaceId });

  const listQuery = useQuery({
    queryKey: queryKeys.notifications.list(workspaceId, {
      unreadOnly: filter === 'unread',
    }),
    queryFn: () =>
      listNotifications({
        workspaceId,
        unreadOnly: filter === 'unread',
        limit: 50,
      }),
  });

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all(workspaceId) });
  };

  const markOne = useMutation({
    mutationFn: (id: string) => markNotificationsRead(workspaceId, { ids: [id] }),
    onMutate: async (id) => {
      queryClient.setQueriesData<NotificationPage>(
        { queryKey: queryKeys.notifications.all(workspaceId) },
        (old) => {
          if (!old?.items) return old;
          return {
            ...old,
            items: old.items.map((item) =>
              item.id === id ? { ...item, readAt: new Date().toISOString() } : item,
            ),
          };
        },
      );
    },
    onSettled: () => void invalidate(),
  });

  const markAll = useMutation({
    mutationFn: () => markNotificationsRead(workspaceId, {}),
    onSuccess: () => void invalidate(),
  });

  const items = listQuery.data?.items ?? [];

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6" data-testid="notifications-page">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t('title')}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{t('pageDescription')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant="secondary"
            size="sm"
            disabled={markAll.isPending}
            onClick={() => markAll.mutate()}
            data-testid="notifications-page-mark-all"
          >
            {t('markAllRead')}
          </Button>
          <Button asChild variant="outline" size="sm">
            <Link href={`/app/${workspaceId}/settings/notifications`}>{t('settingsLink')}</Link>
          </Button>
        </div>
      </header>

      <Tabs value={filter} onValueChange={(v) => setFilter(v as 'all' | 'unread')}>
        <TabsList>
          <TabsTrigger value="all">{t('filterAll')}</TabsTrigger>
          <TabsTrigger value="unread">{t('filterUnread')}</TabsTrigger>
        </TabsList>
      </Tabs>

      {listQuery.isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      ) : listQuery.isError ? (
        <EmptyState
          icon={<BellOff className="h-6 w-6" aria-hidden />}
          title={t('errorTitle')}
          description={t('errorBody')}
          action={
            <Button type="button" onClick={() => void listQuery.refetch()}>
              {t('retry')}
            </Button>
          }
        />
      ) : items.length === 0 ? (
        <EmptyState
          icon={<BellOff className="h-6 w-6" aria-hidden />}
          title={t('emptyTitle')}
          description={t('emptyBody')}
        />
      ) : (
        <div className="divide-y divide-border rounded-md border border-border" role="list">
          {items.map((item) => (
            <div key={item.id} role="listitem">
              <NotificationItem
                notification={item}
                workspaceId={workspaceId}
                onMarkRead={(id) => markOne.mutate(id)}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
