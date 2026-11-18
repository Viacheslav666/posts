'use client';

import type { Channel } from '@posts/api-client';
import {
  Button,
  EmptyState,
  Skeleton,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@posts/ui';
import { useQuery } from '@tanstack/react-query';
import { ShieldOff, Workflow } from 'lucide-react';
import Link from 'next/link';
import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { listChannels } from '@/features/channels/api/channels-api';
import { listProjects } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';

import { listIngestRules } from '../api/grabber-api';
import { ModerationQueue } from './moderation-queue';
import { RuleCard } from './rule-card';

type GrabberPageProps = {
  workspaceId: string;
};

export function GrabberPage({ workspaceId }: GrabberPageProps) {
  const t = useTranslations('grabber');
  const canView = useCan('automations.view', workspaceId);
  const canManage = useCan('automations.manage', workspaceId);
  const canModerate = useCan('automations.moderate', workspaceId);
  const [tab, setTab] = useState('queue');

  const projectsQuery = useQuery({
    queryKey: queryKeys.projects.list(workspaceId),
    queryFn: () => listProjects(workspaceId),
    enabled: canView,
  });
  const projectId = projectsQuery.data?.items?.[0]?.id;

  const rulesQuery = useQuery({
    queryKey: queryKeys.ingest.rules(workspaceId, projectId ?? '', {}),
    queryFn: () => listIngestRules({ workspaceId, projectId: projectId! }),
    enabled: canView && Boolean(projectId),
  });

  const channelsQuery = useQuery({
    queryKey: queryKeys.channels.list(workspaceId),
    queryFn: () => listChannels({ workspaceId, limit: 100 }),
    enabled: canView,
  });

  const rules = useMemo(() => rulesQuery.data?.items ?? [], [rulesQuery.data?.items]);
  const channelsById = useMemo(() => {
    const result = new Map<string, Channel>();
    for (const channel of channelsQuery.data?.items ?? []) {
      if (channel.id) result.set(channel.id, channel);
    }
    return result;
  }, [channelsQuery.data?.items]);

  if (!canView) {
    return (
      <EmptyState
        icon={<ShieldOff className="h-6 w-6" aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
      />
    );
  }

  if (projectsQuery.isLoading || rulesQuery.isLoading) {
    return (
      <div className="space-y-4" data-testid="grabber-loading">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6" data-testid="grabber-page">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{t('eyebrow')}</p>
          <h1 className="text-2xl font-semibold tracking-tight">{t('title')}</h1>
          <p className="mt-1 max-w-2xl text-sm text-muted-foreground">{t('description')}</p>
        </div>
        {canManage ? (
          <Button asChild data-testid="grabber-create-rule">
            <Link href={`/app/${workspaceId}/automations/grabber/new`}>{t('createRule')}</Link>
          </Button>
        ) : null}
      </header>

      <Tabs value={tab} onValueChange={setTab}>
        <TabsList aria-label={t('tabsLabel')}>
          <TabsTrigger value="queue" data-testid="grabber-tab-queue">
            {t('tabs.queue')}
          </TabsTrigger>
          <TabsTrigger value="rules" data-testid="grabber-tab-rules">
            {t('tabs.rules')}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="queue" className="mt-4">
          <ModerationQueue
            workspaceId={workspaceId}
            rules={rules}
            canModerate={canModerate}
          />
        </TabsContent>

        <TabsContent value="rules" className="mt-4">
          <section aria-label={t('tabs.rules')} data-testid="grabber-rules-list">
            <div className="mb-3">
              <h2 className="text-lg font-semibold">{t('activeRules')}</h2>
              <p className="text-sm text-muted-foreground">{t('activeRulesHint')}</p>
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
                    channel={rule.targetChannelId ? channelsById.get(rule.targetChannelId) : undefined}
                    canManage={canManage}
                  />
                ))}
              </div>
            )}
          </section>
        </TabsContent>
      </Tabs>
    </div>
  );
}
