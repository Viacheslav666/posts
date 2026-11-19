'use client';

import {
  Button,
  EmptyState,
  Skeleton,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  toast,
} from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, ShieldOff } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { useTranslations } from 'next-intl';

import { CapabilityNotice, ChannelEventStatus } from '@/features/automations-common';
import { listChannels } from '@/features/channels/api/channels-api';
import { listProjects } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';
import { getErrorMessage } from '@/shared/lib/errors';

import {
  activateSuggestSettings,
  getSuggestSettings,
  pauseSuggestSettings,
  resumeSuggestSettings,
} from '../api/suggest-api';
import { channelHasPublishSuggested } from '../model/capabilities';
import { CopySettingsDialog } from './copy-settings-dialog';
import { HistoryPanel } from './history-panel';
import { ModerationQueue } from './moderation-queue';
import { SettingsForm } from './settings-form';
import { SettingsStatusBadge } from './settings-status-badge';

type SettingsDetailPageProps = {
  workspaceId: string;
  settingsId: string;
};

export function SettingsDetailPage({ workspaceId, settingsId }: SettingsDetailPageProps) {
  const t = useTranslations('automations.suggest');
  const canView = useCan('automations.view', workspaceId);
  const canManage = useCan('automations.manage', workspaceId);
  const canModerate = useCan('automations.moderate', workspaceId);
  const queryClient = useQueryClient();
  const [copyOpen, setCopyOpen] = useState(false);

  const settingsQuery = useQuery({
    queryKey: queryKeys.suggest.setting(workspaceId, settingsId),
    queryFn: () => getSuggestSettings(settingsId),
    enabled: canView,
  });

  const projectsQuery = useQuery({
    queryKey: queryKeys.projects.list(workspaceId),
    queryFn: () => listProjects(workspaceId),
    enabled: canView,
  });

  const channelsQuery = useQuery({
    queryKey: queryKeys.channels.list(workspaceId),
    queryFn: () => listChannels({ workspaceId, limit: 100 }),
    enabled: canView,
  });

  const actionMutation = useMutation({
    mutationFn: async (action: 'activate' | 'pause' | 'resume') => {
      if (action === 'activate') return activateSuggestSettings(settingsId);
      if (action === 'pause') return pauseSuggestSettings(settingsId);
      return resumeSuggestSettings(settingsId);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.suggest.all(workspaceId) });
      toast({ title: t('actionSuccess') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('actionError'),
        description: getErrorMessage(error),
      });
    },
  });

  if (!canView) {
    return (
      <EmptyState
        icon={<ShieldOff className="h-6 w-6" aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
      />
    );
  }

  if (settingsQuery.isError || projectsQuery.isError || channelsQuery.isError) {
    const error = settingsQuery.error ?? projectsQuery.error ?? channelsQuery.error;
    return (
      <EmptyState
        role="alert"
        title={t('loadErrorTitle')}
        description={getErrorMessage(error)}
        action={
          <Button type="button" variant="outline" onClick={() => settingsQuery.refetch()}>
            {t('retry')}
          </Button>
        }
      />
    );
  }

  if (settingsQuery.isLoading || projectsQuery.isLoading || channelsQuery.isLoading) {
    return <Skeleton className="h-64 w-full" />;
  }

  const settings = settingsQuery.data;
  const project = projectsQuery.data?.items?.[0];
  const channels = (channelsQuery.data?.items ?? []).filter(
    (item) => !item.workspaceId || item.workspaceId === workspaceId,
  );
  const channel = channels.find((item) => item.id === settings?.channelId);
  const foreignWorkspace = Boolean(settings?.workspaceId && settings.workspaceId !== workspaceId);

  if (!settings || foreignWorkspace || !project) {
    return (
      <EmptyState title={t('notFoundTitle')} description={t('notFoundBody')} />
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6" data-testid="suggest-settings-detail">
      <div className="flex flex-wrap items-center gap-3">
        <Button asChild variant="ghost" size="sm">
          <Link href={`/app/${workspaceId}/automations/suggest`}>
            <ArrowLeft className="mr-1 h-4 w-4" aria-hidden />
            {t('back')}
          </Link>
        </Button>
        <SettingsStatusBadge status={settings.status} />
        <span className="text-sm text-muted-foreground">
          {channel?.title ?? t('unknownChannel')} · {t(`mode.${settings.mode ?? 'manual'}`)}
        </span>
      </div>

      {channel?.id ? (
        <ChannelEventStatus workspaceId={workspaceId} channelId={channel.id} />
      ) : null}
      {!channelHasPublishSuggested(channel) ? <CapabilityNotice variant="vk_user_wall" /> : null}

      {canManage ? (
        <div className="flex flex-wrap gap-2">
          {settings.status !== 'active' ? (
            <Button
              size="sm"
              onClick={() => actionMutation.mutate('activate')}
              data-testid="suggest-activate"
            >
              {t('actions.activate')}
            </Button>
          ) : (
            <Button
              size="sm"
              variant="outline"
              onClick={() => actionMutation.mutate('pause')}
              data-testid="suggest-pause"
            >
              {t('actions.pause')}
            </Button>
          )}
          {settings.status === 'paused' || settings.status === 'auto_paused' ? (
            <Button
              size="sm"
              onClick={() => actionMutation.mutate('resume')}
              data-testid="suggest-resume"
            >
              {t('actions.resume')}
            </Button>
          ) : null}
          <Button
            size="sm"
            variant="outline"
            onClick={() => setCopyOpen(true)}
            data-testid="suggest-copy-open"
          >
            {t('actions.copy')}
          </Button>
        </div>
      ) : null}

      <Tabs defaultValue="queue">
        <TabsList aria-label={t('tabsLabel')}>
          <TabsTrigger value="queue" data-testid="suggest-tab-queue">
            {t('tabs.queue')}
          </TabsTrigger>
          <TabsTrigger value="settings" data-testid="suggest-tab-settings">
            {t('tabs.settings')}
          </TabsTrigger>
          <TabsTrigger value="history" data-testid="suggest-tab-history">
            {t('tabs.history')}
          </TabsTrigger>
        </TabsList>
        <TabsContent value="queue" className="mt-4">
          <ModerationQueue
            workspaceId={workspaceId}
            settings={settings}
            canModerate={canModerate}
            canManage={canManage}
          />
        </TabsContent>
        <TabsContent value="settings" className="mt-4">
          <SettingsForm
            workspaceId={workspaceId}
            project={project}
            channels={channels}
            settings={settings}
            canManage={canManage}
          />
        </TabsContent>
        <TabsContent value="history" className="mt-4">
          <HistoryPanel workspaceId={workspaceId} settings={settings} />
        </TabsContent>
      </Tabs>

      <CopySettingsDialog
        open={copyOpen}
        onOpenChange={setCopyOpen}
        settings={settings}
        channels={channels}
        onCopied={() =>
          queryClient.invalidateQueries({ queryKey: queryKeys.suggest.all(workspaceId) })
        }
      />
    </div>
  );
}
