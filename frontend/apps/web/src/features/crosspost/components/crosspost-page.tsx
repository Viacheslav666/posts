'use client';

import type { Channel } from '@posts/api-client';
import { Button, EmptyState, Skeleton } from '@posts/ui';
import { useQuery } from '@tanstack/react-query';
import { Radio, ShieldOff, Workflow } from 'lucide-react';
import Link from 'next/link';
import { useMemo } from 'react';
import { useTranslations } from 'next-intl';

import { ChannelEventStatus } from '@/features/automations-common';
import { PlanLimitPaywall } from '@/features/billing/components/plan-limit-paywall';
import { listChannels } from '@/features/channels/api/channels-api';
import { listProjects } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';

import { listCrosspostRules } from '../api/crosspost-api';
import { SOURCE_NETWORKS } from '../model/types';
import { RuleCard } from './rule-card';

type CrosspostPageProps = {
  workspaceId: string;
};

const RULE_SOFT_LIMIT = 20;

export function CrosspostPage({ workspaceId }: CrosspostPageProps) {
  const t = useTranslations('automations.crosspost');
  const canView = useCan('automations.view', workspaceId);
  const canManage = useCan('automations.manage', workspaceId);

  const projectsQuery = useQuery({
    queryKey: queryKeys.projects.list(workspaceId),
    queryFn: () => listProjects(workspaceId),
    enabled: canView,
  });
  const projectId = projectsQuery.data?.items?.[0]?.id;

  const rulesQuery = useQuery({
    queryKey: queryKeys.crosspost.rules(workspaceId, projectId ?? ''),
    queryFn: () => listCrosspostRules({ workspaceId, projectId: projectId! }),
    enabled: canView && Boolean(projectId),
  });

  const channelsQuery = useQuery({
    queryKey: queryKeys.channels.list(workspaceId),
    queryFn: () => listChannels({ workspaceId, limit: 100 }),
    enabled: canView,
  });

  const rules = useMemo(() => rulesQuery.data?.items ?? [], [rulesQuery.data?.items]);
  const channels = useMemo(() => channelsQuery.data?.items ?? [], [channelsQuery.data?.items]);
  const channelsById = useMemo(() => {
    const map = new Map<string, Channel>();
    for (const channel of channels) {
      if (channel.id) map.set(channel.id, channel);
    }
    return map;
  }, [channels]);

  const sourceChannels = channels.filter(
    (channel) =>
      channel.status === 'active' &&
      channel.network &&
      SOURCE_NETWORKS.includes(channel.network as (typeof SOURCE_NETWORKS)[number]),
  );

  const atLimit = rules.length >= RULE_SOFT_LIMIT;
  const firstSourceId = sourceChannels[0]?.id;

  if (!canView) {
    return (
      <EmptyState
        icon={<ShieldOff className="h-6 w-6" aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
      />
    );
  }

  if (projectsQuery.isLoading || rulesQuery.isLoading || channelsQuery.isLoading) {
    return (
      <div className="space-y-4" data-testid="crosspost-loading">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6" data-testid="crosspost-page">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{t('eyebrow')}</p>
          <h1 className="text-2xl font-semibold tracking-tight">{t('title')}</h1>
          <p className="mt-1 max-w-2xl text-sm text-muted-foreground">{t('description')}</p>
        </div>
        {canManage && !atLimit && sourceChannels.length > 0 ? (
          <Button asChild data-testid="crosspost-create-rule">
            <Link href={`/app/${workspaceId}/automations/crosspost/new`}>{t('createRule')}</Link>
          </Button>
        ) : null}
      </header>

      {atLimit ? (
        <PlanLimitPaywall
          workspaceId={workspaceId}
          limitKey="crosspost_rules"
          used={rules.length}
          limit={RULE_SOFT_LIMIT}
        />
      ) : null}

      {sourceChannels.length === 0 ? (
        <EmptyState
          icon={<Radio className="h-6 w-6" aria-hidden />}
          title={t('emptySourcesTitle')}
          description={t('emptySourcesBody')}
        />
      ) : null}

      {firstSourceId ? (
        <div className="space-y-2">
          <h2 className="text-sm font-medium">{t('eventStatusTitle')}</h2>
          <ChannelEventStatus workspaceId={workspaceId} channelId={firstSourceId} />
        </div>
      ) : null}

      <section aria-label={t('rulesTitle')} data-testid="crosspost-rules-list">
        <div className="mb-3">
          <h2 className="text-lg font-semibold">{t('rulesTitle')}</h2>
          <p className="text-sm text-muted-foreground">{t('rulesHint')}</p>
        </div>
        {rules.length === 0 ? (
          <EmptyState
            icon={<Workflow className="h-6 w-6" aria-hidden />}
            title={t('emptyRulesTitle')}
            description={t('emptyRulesBody')}
          />
        ) : (
          <div>
            {rules.map((rule) => (
              <RuleCard
                key={rule.id}
                rule={rule}
                workspaceId={workspaceId}
                sourceChannel={
                  rule.sourceChannelId ? channelsById.get(rule.sourceChannelId) : undefined
                }
                targetChannels={(rule.targets ?? [])
                  .map((target) =>
                    target.targetChannelId ? channelsById.get(target.targetChannelId) : undefined,
                  )
                  .filter((channel): channel is Channel => Boolean(channel))}
                canManage={canManage}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
