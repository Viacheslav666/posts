'use client';

import type { Channel } from '@posts/api-client';
import {
  Button,
  EmptyState,
  NetworkIcon,
  Skeleton,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@posts/ui';
import { useQuery } from '@tanstack/react-query';
import { Radio, ShieldOff } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { listProjects, getWorkspace } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';

import { listChannels } from '../api/channels-api';
import { toSocialNetwork } from '../model/network-map';
import { channelLimitForPlan } from '../model/plan-limits';
import { CONNECTABLE_NETWORKS, type ConnectableNetwork } from '../model/types';
import { ChannelCard } from './channel-card';
import { ChannelSetsPanel } from './channel-sets-panel';
import { ConnectFirstChannel } from './connect-first-channel';
import { ConnectWizardDialog } from './connect-wizard-dialog';
import { PlanLimitBanner } from './plan-limit-banner';

type ChannelsPageProps = {
  workspaceId: string;
};

function groupByNetwork(channels: Channel[]): Record<string, Channel[]> {
  const groups: Record<string, Channel[]> = {
    vk: [],
    tg: [],
    ok: [],
    max: [],
    rutube: [],
  };
  for (const channel of channels) {
    const key = channel.network ?? 'vk';
    if (!groups[key]) {
      groups[key] = [];
    }
    groups[key].push(channel);
  }
  return groups;
}

export function ChannelsPage({ workspaceId }: ChannelsPageProps) {
  const t = useTranslations('channels');
  const canView = useCan('channels.view', workspaceId);
  const canManage = useCan('channels.manage', workspaceId);
  const canManageSets = useCan('channel_sets.manage', workspaceId);
  const [wizardNetwork, setWizardNetwork] = useState<ConnectableNetwork | null>(null);
  const [showPlanLimit, setShowPlanLimit] = useState(false);

  const channelsQuery = useQuery({
    queryKey: queryKeys.channels.list(workspaceId),
    queryFn: () => listChannels({ workspaceId, limit: 100 }),
    enabled: canView,
  });

  const workspaceQuery = useQuery({
    queryKey: queryKeys.workspaces.detail(workspaceId),
    queryFn: () => getWorkspace(workspaceId),
    enabled: canView,
  });

  const projectsQuery = useQuery({
    queryKey: queryKeys.projects.list(workspaceId),
    queryFn: () => listProjects(workspaceId),
    enabled: canView,
  });

  const channels = useMemo(() => channelsQuery.data?.items ?? [], [channelsQuery.data?.items]);
  const projects = projectsQuery.data?.items ?? [];
  const grouped = useMemo(() => groupByNetwork(channels), [channels]);
  const used = channels.filter((channel) => channel.status !== 'disabled').length;
  const limit = channelLimitForPlan(workspaceQuery.data?.planCode);

  if (!canView) {
    return (
      <EmptyState
        icon={<ShieldOff className="h-6 w-6" aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
      />
    );
  }

  if (channelsQuery.isLoading || workspaceQuery.isLoading) {
    return (
      <div className="space-y-4" data-testid="channels-loading">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6" data-testid="channels-page">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t('title')}</h1>
          <p className="text-sm text-muted-foreground">{t('description')}</p>
        </div>
        {canManage ? (
          <Button type="button" onClick={() => setWizardNetwork('vk')} data-testid="channels-connect">
            {t('connect.cta')}
          </Button>
        ) : null}
      </header>

      <PlanLimitBanner
        used={used}
        limit={limit}
        workspaceId={workspaceId}
        showUpgrade={showPlanLimit}
      />

      {channels.length === 0 ? (
        canManage ? (
          <ConnectFirstChannel
            workspaceId={workspaceId}
            variant="onboarding"
            onConnected={() => void channelsQuery.refetch()}
            onPlanLimit={() => setShowPlanLimit(true)}
          />
        ) : (
          <EmptyState
            icon={<Radio className="h-6 w-6" aria-hidden />}
            title={t('emptyTitle')}
            description={t('emptyBody')}
          />
        )
      ) : (
        <Tabs defaultValue="grid">
          <TabsList>
            <TabsTrigger value="grid">{t('tabs.grid')}</TabsTrigger>
            <TabsTrigger value="sets">{t('tabs.sets')}</TabsTrigger>
            {canManage ? (
              <TabsTrigger value="connect">{t('tabs.connect')}</TabsTrigger>
            ) : null}
          </TabsList>

          <TabsContent value="grid" className="space-y-8 pt-4">
            {CONNECTABLE_NETWORKS.map((network) => {
              const items = grouped[network] ?? [];
              const icon = toSocialNetwork(network);
              return (
                <section key={network} aria-label={t(`networks.${network}`)} className="space-y-3">
                  <div className="flex items-center justify-between gap-2">
                    <h2 className="flex items-center gap-2 text-lg font-semibold">
                      {icon ? <NetworkIcon network={icon} size="sm" /> : null}
                      {t(`networks.${network}`)}
                      <span className="text-sm font-normal text-muted-foreground">
                        ({items.length})
                      </span>
                    </h2>
                    {canManage ? (
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => setWizardNetwork(network)}
                        data-testid={`connect-${network}`}
                      >
                        {t('connect.add')}
                      </Button>
                    ) : null}
                  </div>
                  {items.length === 0 ? (
                    <p className="text-sm text-muted-foreground">{t('grid.emptyNetwork')}</p>
                  ) : (
                    <ul className="grid gap-3 sm:grid-cols-2">
                      {items.map((channel) => (
                        <ChannelCard
                          key={channel.id}
                          channel={channel}
                          workspaceId={workspaceId}
                          projects={projects}
                          canManage={canManage}
                          onReconnect={(net) => setWizardNetwork(net)}
                        />
                      ))}
                    </ul>
                  )}
                </section>
              );
            })}
          </TabsContent>

          <TabsContent value="sets" className="pt-4">
            <ChannelSetsPanel
              workspaceId={workspaceId}
              channels={channels}
              canManage={canManageSets}
            />
          </TabsContent>

          {canManage ? (
            <TabsContent value="connect" className="pt-4">
              <ConnectFirstChannel
                workspaceId={workspaceId}
                onConnected={() => void channelsQuery.refetch()}
                onPlanLimit={() => setShowPlanLimit(true)}
              />
            </TabsContent>
          ) : null}
        </Tabs>
      )}

      <ConnectWizardDialog
        network={wizardNetwork}
        workspaceId={workspaceId}
        open={wizardNetwork !== null}
        onOpenChange={(open) => {
          if (!open) setWizardNetwork(null);
        }}
        onConnected={() => void channelsQuery.refetch()}
        onPlanLimit={() => setShowPlanLimit(true)}
      />
    </div>
  );
}
