'use client';

import type { AntispamRule, Channel } from '@posts/api-client';
import { Button, NetworkIcon, toast } from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowDown, ArrowUp } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

import { ChannelEventStatus } from '@/features/automations-common';
import { toSocialNetwork } from '@/features/channels/model/network-map';
import { queryKeys } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import {
  activateAntispamRule,
  getAntispamRuleStats,
  patchAntispamRule,
  pauseAntispamRule,
} from '../api/antispam-api';
import { RuleStatusBadge } from './rule-status-badge';

type RuleCardProps = {
  rule: AntispamRule;
  workspaceId: string;
  channel?: Channel;
  canManage: boolean;
  siblingPriorities: number[];
};

export function RuleCard({
  rule,
  workspaceId,
  channel,
  canManage,
  siblingPriorities,
}: RuleCardProps) {
  const t = useTranslations('automations.antispam.rules');
  const queryClient = useQueryClient();

  const statsQuery = useQuery({
    queryKey: queryKeys.antispam.stats(workspaceId, rule.id!),
    queryFn: () => getAntispamRuleStats(rule.id!),
    enabled: Boolean(rule.id),
  });

  const actionMutation = useMutation({
    mutationFn: async (action: 'pause' | 'activate') => {
      const id = rule.id!;
      if (action === 'pause') return pauseAntispamRule(id);
      return activateAntispamRule(id);
    },
    onSuccess: async (_data, action) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.antispam.all(workspaceId) });
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

  const priorityMutation = useMutation({
    mutationFn: async (direction: 'up' | 'down') => {
      const current = rule.priority ?? 100;
      const sorted = [...new Set(siblingPriorities)].sort((a, b) => a - b);
      const idx = sorted.indexOf(current);
      let next = current;
      if (direction === 'up') {
        next = idx > 0 ? sorted[idx - 1]! - 1 : current - 10;
      } else {
        next = idx >= 0 && idx < sorted.length - 1 ? sorted[idx + 1]! + 1 : current + 10;
      }
      return patchAntispamRule(rule.id!, {
        priority: Math.max(0, next),
        version: rule.version ?? 1,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.antispam.all(workspaceId) });
      toast({ title: t('prioritySuccess') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  const networkIcon = toSocialNetwork(channel?.network ?? '');
  const hits24h = statsQuery.data?.hits24h ?? 0;
  const hits7d = statsQuery.data?.hits7d ?? 0;

  return (
    <article
      data-testid={`antispam-rule-${rule.id}`}
      className="flex flex-col gap-3 border-b border-border py-4 last:border-b-0 sm:flex-row sm:items-start sm:justify-between"
    >
      <div className="min-w-0 space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <Link
            href={`/app/${workspaceId}/automations/antispam/${rule.id}`}
            className="text-base font-semibold text-foreground hover:underline"
          >
            {rule.name}
          </Link>
          <RuleStatusBadge status={rule.status} />
          {rule.dryRun ? (
            <span
              className="rounded-md border border-border px-1.5 py-0.5 text-xs text-muted-foreground"
              data-testid={`antispam-dry-run-${rule.id}`}
            >
              {t('dryRunBadge')}
            </span>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          {networkIcon ? <NetworkIcon network={networkIcon} size="sm" /> : null}
          <span>{channel?.title ?? rule.channelId}</span>
          <span aria-hidden>·</span>
          <span>{t('priority', { value: rule.priority ?? 0 })}</span>
        </div>

        <p className="text-xs text-muted-foreground" data-testid={`antispam-stats-${rule.id}`}>
          {statsQuery.isError ? t('statsUnavailable') : t('stats', { hits24h, hits7d })}
        </p>

        {rule.channelId ? (
          <ChannelEventStatus workspaceId={workspaceId} channelId={rule.channelId} compact />
        ) : null}
      </div>

      <div className="flex flex-wrap gap-2">
        {canManage ? (
          <>
            <Button
              size="sm"
              variant="outline"
              aria-label={t('priorityUp')}
              disabled={priorityMutation.isPending}
              onClick={() => priorityMutation.mutate('up')}
              data-testid={`antispam-priority-up-${rule.id}`}
            >
              <ArrowUp className="h-4 w-4" aria-hidden />
            </Button>
            <Button
              size="sm"
              variant="outline"
              aria-label={t('priorityDown')}
              disabled={priorityMutation.isPending}
              onClick={() => priorityMutation.mutate('down')}
              data-testid={`antispam-priority-down-${rule.id}`}
            >
              <ArrowDown className="h-4 w-4" aria-hidden />
            </Button>
          </>
        ) : null}
        {canManage && rule.status === 'active' ? (
          <Button
            size="sm"
            variant="outline"
            disabled={actionMutation.isPending}
            onClick={() => actionMutation.mutate('pause')}
            data-testid={`antispam-pause-${rule.id}`}
          >
            {t('pause')}
          </Button>
        ) : null}
        {canManage && (rule.status === 'paused' || rule.status === 'draft') ? (
          <Button
            size="sm"
            variant="outline"
            disabled={actionMutation.isPending}
            onClick={() => actionMutation.mutate('activate')}
            data-testid={`antispam-activate-${rule.id}`}
          >
            {t('activate')}
          </Button>
        ) : null}
        <Button size="sm" variant="ghost" asChild>
          <Link href={`/app/${workspaceId}/automations/antispam/${rule.id}`}>{t('open')}</Link>
        </Button>
      </div>
    </article>
  );
}
