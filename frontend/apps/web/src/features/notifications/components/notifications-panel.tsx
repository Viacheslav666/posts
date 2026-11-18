'use client';

import type { NotificationPage } from '@posts/api-client';
import {
  Button,
  EmptyState,
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  Skeleton,
} from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BellOff } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';

import { listNotifications, markNotificationsRead } from '../api/notifications-api';
import { useNotificationUpdates } from '../hooks/use-notification-updates';
import { NotificationItem } from './notification-item';

type NotificationsPanelProps = {
  workspaceId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

export function NotificationsPanel({ workspaceId, open, onOpenChange }: NotificationsPanelProps) {
  const t = useTranslations('notifications');
  const queryClient = useQueryClient();
  useNotificationUpdates({ workspaceId, enabled: open });

  const listQuery = useQuery({
    queryKey: queryKeys.notifications.list(workspaceId, {}),
    queryFn: () => listNotifications({ workspaceId, limit: 30 }),
    enabled: open,
  });

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all(workspaceId) });
  };

  const markOne = useMutation({
    mutationFn: (id: string) => markNotificationsRead(workspaceId, { ids: [id] }),
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: queryKeys.notifications.all(workspaceId) });
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
      queryClient.setQueryData<number>(queryKeys.notifications.unreadCount(workspaceId), (prev) =>
        typeof prev === 'number' ? Math.max(0, prev - 1) : prev,
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
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-md" data-testid="notifications-panel">
        <SheetHeader>
          <SheetTitle>{t('title')}</SheetTitle>
          <SheetDescription>{t('panelDescription')}</SheetDescription>
        </SheetHeader>

        <div className="mt-4 flex items-center justify-between gap-2">
          <Button
            type="button"
            variant="secondary"
            size="sm"
            disabled={markAll.isPending || items.every((i) => i.readAt)}
            onClick={() => markAll.mutate()}
            data-testid="notifications-mark-all"
          >
            {t('markAllRead')}
          </Button>
          <Button asChild variant="ghost" size="sm">
            <Link href={`/app/${workspaceId}/notifications`} onClick={() => onOpenChange(false)}>
              {t('openPage')}
            </Link>
          </Button>
        </div>

        <div className="mt-4 flex-1 overflow-y-auto" role="list" aria-label={t('title')}>
          {listQuery.isLoading ? (
            <div className="space-y-2">
              <Skeleton className="h-14 w-full" />
              <Skeleton className="h-14 w-full" />
              <Skeleton className="h-14 w-full" />
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
            items.map((item) => (
              <div key={item.id} role="listitem">
                <NotificationItem
                  notification={item}
                  workspaceId={workspaceId}
                  compact
                  onMarkRead={(id) => markOne.mutate(id)}
                />
              </div>
            ))
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
