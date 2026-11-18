'use client';

import { Button } from '@posts/ui';
import { useQuery } from '@tanstack/react-query';
import { Bell } from 'lucide-react';
import { useState } from 'react';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';

import { listNotifications } from '../api/notifications-api';
import { useNotificationUpdates } from '../hooks/use-notification-updates';
import { NotificationsPanel } from './notifications-panel';

type NotificationsBellProps = {
  workspaceId: string;
};

export function NotificationsBell({ workspaceId }: NotificationsBellProps) {
  const t = useTranslations('common');
  const tn = useTranslations('notifications');
  const [open, setOpen] = useState(false);

  useNotificationUpdates({ workspaceId, enabled: true });

  const unreadQuery = useQuery({
    queryKey: queryKeys.notifications.unreadCount(workspaceId),
    queryFn: async () => {
      const page = await listNotifications({ workspaceId, unreadOnly: true, limit: 100 });
      return page.items?.length ?? 0;
    },
    refetchInterval: 30_000,
  });

  const count = unreadQuery.data ?? 0;
  const label =
    count > 0 ? tn('bellWithCount', { count: count > 99 ? '99+' : String(count) }) : t('notifications');

  return (
    <>
      <Button
        variant="ghost"
        size="icon"
        aria-label={label}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen(true)}
        className="relative"
        data-testid="notifications-bell"
      >
        <Bell aria-hidden />
        {count > 0 ? (
          <span
            className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-semibold text-destructive-foreground"
            data-testid="notifications-unread-count"
            aria-hidden
          >
            {count > 99 ? '99+' : count}
          </span>
        ) : null}
      </Button>
      <NotificationsPanel workspaceId={workspaceId} open={open} onOpenChange={setOpen} />
    </>
  );
}
