'use client';

import { Badge, Button, Skeleton, cn } from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { formatDistanceToNow } from 'date-fns';
import { ru } from 'date-fns/locale';
import { Wrench } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';

import {
  getEventSubscription,
  repairEventSubscription,
} from '../api/event-subscription-api';
import {
  isRepairableStatus,
  networkHintKey,
  toEventSubscriptionUiStatus,
  type EventSubscriptionUiStatus,
} from '../model/event-subscription';

const BADGE_VARIANT: Record<
  EventSubscriptionUiStatus,
  'published' | 'review' | 'failed' | 'paused' | 'outline'
> = {
  connected: 'published',
  pending: 'review',
  failed: 'failed',
  disabled: 'paused',
  unknown: 'outline',
};

export type ChannelEventStatusProps = {
  workspaceId: string;
  channelId: string;
  className?: string;
  compact?: boolean;
};

export function ChannelEventStatus({
  workspaceId,
  channelId,
  className,
  compact = false,
}: ChannelEventStatusProps) {
  const t = useTranslations('automations.common.eventStatus');
  const canManage = useCan('automations.manage', workspaceId);
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: queryKeys.channels.eventSubscription(workspaceId, channelId),
    queryFn: () => getEventSubscription(channelId),
    enabled: Boolean(channelId),
  });

  const repair = useMutation({
    mutationFn: () => repairEventSubscription(channelId),
    onSuccess: async (data) => {
      queryClient.setQueryData(
        queryKeys.channels.eventSubscription(workspaceId, channelId),
        data,
      );
      await queryClient.invalidateQueries({
        queryKey: queryKeys.channels.eventSubscription(workspaceId, channelId),
      });
    },
  });

  if (query.isLoading) {
    return (
      <div data-testid="channel-event-status-loading" className={className}>
        <Skeleton className="h-8 w-40" />
      </div>
    );
  }

  if (query.isError || !query.data) {
    return (
      <div
        role="alert"
        data-testid="channel-event-status-error"
        className={cn('rounded-md border border-destructive/40 bg-destructive/5 p-3 text-sm', className)}
      >
        <p className="font-medium">{t('loadErrorTitle')}</p>
        <p className="mt-1 text-muted-foreground">{t('loadErrorBody')}</p>
      </div>
    );
  }

  const sub = query.data;
  const uiStatus = toEventSubscriptionUiStatus(sub.status);
  const hintKey = networkHintKey(sub.network);
  const showRepair = canManage && isRepairableStatus(sub.status);
  const lastEventLabel = sub.lastEventAt
    ? formatDistanceToNow(new Date(sub.lastEventAt), { addSuffix: true, locale: ru })
    : t('never');

  return (
    <section
      className={cn(
        'rounded-md border border-border bg-card/60 p-3',
        compact && 'p-2',
        className,
      )}
      data-testid="channel-event-status"
      data-status={uiStatus}
      aria-label={t('panelLabel')}
    >
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant={BADGE_VARIANT[uiStatus]} data-testid="channel-event-status-badge">
          {t(`statuses.${uiStatus}`)}
        </Badge>
        {sub.network ? (
          <span className="text-xs uppercase tracking-wide text-muted-foreground">
            {sub.network}
          </span>
        ) : null}
      </div>

      {!compact ? (
        <dl className="mt-3 space-y-1 text-sm">
          <div className="flex flex-wrap gap-x-2">
            <dt className="text-muted-foreground">{t('lastEvent')}</dt>
            <dd data-testid="channel-event-last-at">{lastEventLabel}</dd>
          </div>
          {uiStatus === 'failed' && sub.lastError ? (
            <div className="flex flex-col gap-0.5">
              <dt className="text-muted-foreground">{t('error')}</dt>
              <dd className="text-destructive" data-testid="channel-event-error">
                {sub.lastError}
              </dd>
            </div>
          ) : null}
          {sub.warning ? (
            <div className="flex flex-col gap-0.5">
              <dt className="text-muted-foreground">{t('warning')}</dt>
              <dd data-testid="channel-event-warning">{sub.warning}</dd>
            </div>
          ) : null}
        </dl>
      ) : null}

      <p className="mt-3 text-sm text-muted-foreground" data-testid="channel-event-hint">
        {t(`hints.${hintKey}`)}
      </p>

      {showRepair ? (
        <div className="mt-3">
          <Button
            type="button"
            size="sm"
            variant="secondary"
            disabled={repair.isPending}
            data-testid="channel-event-repair"
            onClick={() => repair.mutate()}
          >
            <Wrench className="h-3.5 w-3.5" aria-hidden />
            {repair.isPending ? t('repairing') : t('repair')}
          </Button>
          {repair.isError ? (
            <p className="mt-2 text-sm text-destructive" role="alert">
              {t('repairError')}
            </p>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}
