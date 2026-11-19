'use client';

import type { Channel, CrosspostItem, CrosspostRule } from '@posts/api-client';
import { Button, NetworkIcon, toast } from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useMemo } from 'react';

import { ChannelEventStatus } from '@/features/automations-common';
import { toSocialNetwork } from '@/features/channels/model/network-map';
import { queryKeys } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import {
  activateCrosspostRule,
  listCrosspostItems,
  pauseCrosspostRule,
} from '../api/crosspost-api';
import { readAutoPauseReason, withConfigDefaults } from '../model/config';
import { aggregate24hCounters } from '../model/stats';
import { RuleStatusBadge } from './rule-status-badge';

type RuleCardProps = {
  rule: CrosspostRule;
  workspaceId: string;
  sourceChannel?: Channel;
  targetChannels: Channel[];
  canManage: boolean;
};

export function RuleCard({
  rule,
  workspaceId,
  sourceChannel,
  targetChannels,
  canManage,
}: RuleCardProps) {
  const t = useTranslations('automations.crosspost.rules');
  const tPause = useTranslations('automations.crosspost.autoPause');
  const queryClient = useQueryClient();

  const itemsQuery = useQuery({
    queryKey: queryKeys.crosspost.items(workspaceId, rule.id!, { forStats: true }),
    queryFn: () => listCrosspostItems({ ruleId: rule.id!, limit: 100 }),
    enabled: Boolean(rule.id),
  });

  const counters = useMemo(
    () => aggregate24hCounters((itemsQuery.data?.items ?? []) as CrosspostItem[]),
    [itemsQuery.data?.items],
  );

  const actionMutation = useMutation({
    mutationFn: async (action: 'pause' | 'activate') => {
      const id = rule.id!;
      if (action === 'pause') return pauseCrosspostRule(id);
      return activateCrosspostRule(id);
    },
    onSuccess: async (_data, action) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.crosspost.all(workspaceId) });
      toast({
        title: action === 'pause' ? t('pauseSuccess') : t('activateSuccess'),
      });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  const sourceIcon = toSocialNetwork(sourceChannel?.network ?? rule.sourceNetwork ?? '');
  const delay = withConfigDefaults(rule.config).delay_minutes;
  const autoPauseReason = readAutoPauseReason(rule);

  return (
    <article
      data-testid={`crosspost-rule-${rule.id}`}
      className="flex flex-col gap-3 border-b border-border py-4 last:border-b-0 sm:flex-row sm:items-start sm:justify-between"
    >
      <div className="min-w-0 space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <Link
            href={`/app/${workspaceId}/automations/crosspost/${rule.id}`}
            className="text-base font-semibold text-foreground hover:underline"
          >
            {rule.name}
          </Link>
          <RuleStatusBadge status={rule.status} />
        </div>

        <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          {sourceIcon ? <NetworkIcon network={sourceIcon} size="sm" /> : null}
          <span>{sourceChannel?.title ?? rule.sourceChannelId}</span>
          <span aria-hidden>→</span>
          <span className="inline-flex flex-wrap items-center gap-1">
            {targetChannels.map((channel) => {
              const icon = toSocialNetwork(channel.network ?? '');
              return (
                <span key={channel.id} className="inline-flex items-center gap-1">
                  {icon ? <NetworkIcon network={icon} size="sm" /> : null}
                  <span className="sr-only">{channel.title}</span>
                </span>
              );
            })}
            {targetChannels.length === 0
              ? (rule.targets ?? []).map((target) => (
                  <span key={target.targetChannelId} className="text-xs">
                    {target.network ?? target.targetChannelId}
                  </span>
                ))
              : null}
          </span>
        </div>

        <p className="text-xs text-muted-foreground">
          {t('delay', { minutes: delay })} · {t('stats24h', counters)}
        </p>

        {rule.status === 'auto_paused' && autoPauseReason ? (
          <p className="text-xs text-destructive" data-testid="crosspost-auto-pause-reason">
            {autoPauseReason === 'circuit_volume' || autoPauseReason === 'circuit_loop_skips'
              ? tPause(autoPauseReason)
              : autoPauseReason}
          </p>
        ) : null}

        {rule.sourceChannelId ? (
          <ChannelEventStatus
            workspaceId={workspaceId}
            channelId={rule.sourceChannelId}
            compact
          />
        ) : null}
      </div>

      <div className="flex flex-wrap gap-2">
        {canManage && rule.status === 'active' ? (
          <Button
            size="sm"
            variant="outline"
            disabled={actionMutation.isPending}
            onClick={() => actionMutation.mutate('pause')}
            data-testid={`crosspost-pause-${rule.id}`}
          >
            {t('pause')}
          </Button>
        ) : null}
        {canManage && (rule.status === 'paused' || rule.status === 'auto_paused' || rule.status === 'draft') ? (
          <Button
            size="sm"
            variant="outline"
            disabled={actionMutation.isPending}
            onClick={() => actionMutation.mutate('activate')}
            data-testid={`crosspost-activate-${rule.id}`}
          >
            {t('activate')}
          </Button>
        ) : null}
        <Button size="sm" variant="ghost" asChild>
          <Link href={`/app/${workspaceId}/automations/crosspost/${rule.id}`}>{t('open')}</Link>
        </Button>
      </div>
    </article>
  );
}
