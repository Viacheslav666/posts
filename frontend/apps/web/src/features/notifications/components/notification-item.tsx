'use client';

import type { Notification } from '@posts/api-client';
import { Badge, Button, cn } from '@posts/ui';
import { formatDistanceToNow } from 'date-fns';
import { ru } from 'date-fns/locale';
import { useTranslations } from 'next-intl';
import Link from 'next/link';

import { isUnread } from '../model/types';

type NotificationItemProps = {
  notification: Notification;
  workspaceId: string;
  onMarkRead?: (id: string) => void;
  compact?: boolean;
};

function payloadDetail(notification: Notification): string | undefined {
  const payload = notification.payload as Record<string, unknown> | undefined;
  if (!payload) return undefined;
  if (typeof payload.errorMessage === 'string' && payload.errorMessage.trim()) {
    return payload.errorMessage;
  }
  if (typeof payload.title === 'string') return payload.title;
  if (typeof payload.ruleName === 'string') return payload.ruleName;
  if (typeof payload.email === 'string') return payload.email;
  return undefined;
}

function hrefFor(notification: Notification, workspaceId: string): string | null {
  const payload = notification.payload as Record<string, unknown> | undefined;
  const type = notification.type ?? '';
  const postId = typeof payload?.postId === 'string' ? payload.postId : '';
  if (
    postId &&
    (type.startsWith('publication.') || type.startsWith('post.') || type.startsWith('story.') || type.startsWith('ord.'))
  ) {
    return `/app/${workspaceId}/posts/${postId}`;
  }
  if (type.startsWith('channel.')) {
    return `/app/${workspaceId}/channels`;
  }
  if (type.startsWith('grabber.')) {
    return `/app/${workspaceId}/automations/grabber`;
  }
  if (type.startsWith('ord.')) {
    return `/app/${workspaceId}/ord`;
  }
  if (type.startsWith('invitation.') || type.startsWith('team.')) {
    return `/app/${workspaceId}/team`;
  }
  return null;
}

export function NotificationItem({
  notification,
  workspaceId,
  onMarkRead,
  compact = false,
}: NotificationItemProps) {
  const t = useTranslations('notifications');
  const unread = isUnread(notification);
  const typeKey = notification.type ?? 'unknown';
  const titleMap: Record<string, string> = {
    'publication.failed': t('types.publication.failed'),
    'publication.succeeded': t('types.publication.succeeded'),
    'publication.waiting_for_channel': t('types.publication.waiting_for_channel'),
    'post.scheduled': t('types.post.scheduled'),
    'post.rescheduled': t('types.post.rescheduled'),
    'post.paused': t('types.post.paused'),
    'post.resumed': t('types.post.resumed'),
    'post.deleted': t('types.post.deleted'),
    'story.scheduled': t('types.story.scheduled'),
    'channel.reauth_required': t('types.channel.reauth_required'),
    'channel.no_permissions': t('types.channel.no_permissions'),
    'channel.event_subscription_degraded': t('types.channel.event_subscription_degraded'),
    'grabber.moderation_queued': t('types.grabber.moderation_queued'),
    'grabber.item_failed': t('types.grabber.item_failed'),
    'grabber.setting_error': t('types.grabber.setting_error'),
    'grabber.error': t('types.grabber.error'),
    'crosspost.rule_auto_paused': t('types.crosspost.rule_auto_paused'),
    'ord.error': t('types.ord.error'),
    'ord.erid_failed': t('types.ord.erid_failed'),
    'ord.problem': t('types.ord.problem'),
    'ord.expiry_warning': t('types.ord.expiry_warning'),
    'ord.display_ended': t('types.ord.display_ended'),
    'billing.low_balance': t('types.billing.low_balance'),
    'billing.access_suspended': t('types.billing.access_suspended'),
    'invitation.created': t('types.invitation.created'),
    'team.approval_requested': t('types.team.approval_requested'),
  };
  const title = titleMap[typeKey] ?? t('types.unknown');
  const detail = payloadDetail(notification);
  const href = hrefFor(notification, workspaceId);
  const when = notification.createdAt
    ? formatDistanceToNow(new Date(notification.createdAt), { addSuffix: true, locale: ru })
    : '';

  const body = (
    <div
      className={cn(
        'flex w-full flex-col gap-1 rounded-md px-3 py-2 text-left transition-colors',
        unread ? 'bg-primary/5' : 'hover:bg-muted/60',
        compact ? 'py-2' : 'py-3',
      )}
      data-testid="notification-item"
      data-unread={unread ? 'true' : 'false'}
    >
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className={cn('text-sm text-foreground', unread && 'font-semibold')}>{title}</p>
            {unread ? (
              <Badge variant="failed" className="px-1.5 py-0 text-[10px]">
                {t('unreadBadge')}
              </Badge>
            ) : null}
          </div>
          {detail ? <p className="truncate text-xs text-muted-foreground">{detail}</p> : null}
          {when ? <p className="text-xs text-muted-foreground">{when}</p> : null}
        </div>
        {unread && onMarkRead && notification.id ? (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="shrink-0"
            onClick={(event) => {
              event.preventDefault();
              event.stopPropagation();
              onMarkRead(notification.id!);
            }}
            data-testid="notification-mark-read"
          >
            {t('markRead')}
          </Button>
        ) : null}
      </div>
    </div>
  );

  if (href) {
    return (
      <Link href={href} className="block focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
        {body}
      </Link>
    );
  }
  return body;
}
