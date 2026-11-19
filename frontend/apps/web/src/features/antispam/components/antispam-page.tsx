'use client';

import type { Channel } from '@posts/api-client';
import { Button, EmptyState, Skeleton } from '@posts/ui';
import { useQuery } from '@tanstack/react-query';
import { BookOpen, Radio, ShieldOff, Shield } from 'lucide-react';
import Link from 'next/link';
import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { ChannelEventStatus } from '@/features/automations-common';
import { listChannels } from '@/features/channels/api/channels-api';
import { listProjects } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';

import { listAntispamRules } from '../api/antispam-api';
import { ANTISPAM_NETWORKS } from '../model/types';
import { RuleCard } from './rule-card';
import { WordListsPanel } from './word-lists-panel';

type AntispamPageProps = {
  workspaceId: string;
};

export function AntispamPage({ workspaceId }: AntispamPageProps) {
  const t = useTranslations('automations.antispam');
  const canView = useCan('automations.view', workspaceId);
  const canManage = useCan('automations.manage', workspaceId);
  const [showDictionaries, setShowDictionaries] = useState(false);

  const projectsQuery = useQuery({
    queryKey: queryKeys.projects.list(workspaceId),
    queryFn: () => listProjects(workspaceId),
    enabled: canView,
  });
  const projectId = projectsQuery.data?.items?.[0]?.id;

  const rulesQuery = useQuery({
    queryKey: queryKeys.antispam.rules(workspaceId, projectId ?? ''),
    queryFn: () => listAntispamRules({ workspaceId, projectId: projectId! }),
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

  const antispamChannels = channels.filter(
    (channel) =>
      channel.status === 'active' &&
      channel.network &&
      ANTISPAM_NETWORKS.includes(channel.network as (typeof ANTISPAM_NETWORKS)[number]),
  );

  const rulesByChannel = useMemo(() => {
    const groups = new Map<string, typeof rules>();
    for (const rule of rules) {
      const key = rule.channelId ?? 'unknown';
      const list = groups.get(key) ?? [];
      list.push(rule);
      groups.set(key, list);
    }
    return groups;
  }, [rules]);

  const siblingPriorities = rules.map((rule) => rule.priority ?? 100);
  const firstChannelId = antispamChannels[0]?.id;

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
      <div className="space-y-4" data-testid="antispam-loading">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6" data-testid="antispam-page">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{t('eyebrow')}</p>
          <h1 className="text-2xl font-semibold tracking-tight">{t('title')}</h1>
          <p className="mt-1 max-w-2xl text-sm text-muted-foreground">{t('description')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {canManage ? (
            <Button
              variant="outline"
              onClick={() => setShowDictionaries((v) => !v)}
              data-testid="antispam-toggle-dictionaries"
            >
              <BookOpen className="mr-1 h-4 w-4" aria-hidden />
              {t('dictionaries')}
            </Button>
          ) : null}
          {canManage && antispamChannels.length > 0 ? (
            <Button asChild data-testid="antispam-create-rule">
              <Link href={`/app/${workspaceId}/automations/antispam/new`}>{t('createRule')}</Link>
            </Button>
          ) : null}
        </div>
      </header>

      {antispamChannels.length === 0 ? (
        <EmptyState
          icon={<Radio className="h-6 w-6" aria-hidden />}
          title={t('emptyChannelsTitle')}
          description={t('emptyChannelsBody')}
        />
      ) : null}

      {firstChannelId ? (
        <div className="space-y-2">
          <h2 className="text-sm font-medium">{t('eventStatusTitle')}</h2>
          <ChannelEventStatus workspaceId={workspaceId} channelId={firstChannelId} />
        </div>
      ) : null}

      {showDictionaries ? (
        <WordListsPanel workspaceId={workspaceId} rules={rules} canManage={canManage} />
      ) : null}

      <section aria-label={t('rulesTitle')} data-testid="antispam-rules-list">
        <div className="mb-3">
          <h2 className="text-lg font-semibold">{t('rulesTitle')}</h2>
          <p className="text-sm text-muted-foreground">{t('rulesHint')}</p>
        </div>
        {rules.length === 0 ? (
          <EmptyState
            icon={<Shield className="h-6 w-6" aria-hidden />}
            title={t('emptyRulesTitle')}
            description={t('emptyRulesBody')}
          />
        ) : (
          <div className="space-y-6">
            {[...rulesByChannel.entries()].map(([channelId, channelRules]) => {
              const channel = channelsById.get(channelId);
              return (
                <div key={channelId} data-testid={`antispam-channel-group-${channelId}`}>
                  <h3 className="mb-2 text-sm font-medium text-muted-foreground">
                    {channel?.title ?? channelId}
                  </h3>
                  {channelRules.map((rule) => (
                    <RuleCard
                      key={rule.id}
                      rule={rule}
                      workspaceId={workspaceId}
                      channel={channel}
                      canManage={canManage}
                      siblingPriorities={siblingPriorities}
                    />
                  ))}
                </div>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}
