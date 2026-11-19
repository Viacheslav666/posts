'use client';

import type { Channel, SuggestSettings } from '@posts/api-client';
import { Button, EmptyState, Skeleton, toast } from '@posts/ui';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Radio, ShieldOff, Workflow } from 'lucide-react';
import Link from 'next/link';
import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import {
  AutomationChannelPicker,
  CapabilityNotice,
  ChannelEventStatus,
} from '@/features/automations-common';
import { PlanLimitPaywall } from '@/features/billing/components/plan-limit-paywall';
import { listChannels } from '@/features/channels/api/channels-api';
import { listProjects } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';
import { getErrorMessage } from '@/shared/lib/errors';

import { createSuggestSettings, listSuggestItems, listSuggestSettings } from '../api/suggest-api';
import { channelHasPublishSuggested } from '../model/capabilities';
import { configAsApiBlob, withConfigDefaults } from '../model/config';
import { SUGGEST_PAGE_SIZE, SUGGEST_SOFT_CHANNEL_LIMIT } from '../model/types';
import { SettingsCard } from './settings-card';

type SuggestPageProps = {
  workspaceId: string;
};

export function SuggestPage({ workspaceId }: SuggestPageProps) {
  const t = useTranslations('automations.suggest');
  const canView = useCan('automations.view', workspaceId);
  const canManage = useCan('automations.manage', workspaceId);
  const queryClient = useQueryClient();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [selectedChannelId, setSelectedChannelId] = useState<string | null>(null);

  const projectsQuery = useQuery({
    queryKey: queryKeys.projects.list(workspaceId),
    queryFn: () => listProjects(workspaceId),
    enabled: canView,
  });
  const projectId = projectsQuery.data?.items?.[0]?.id;

  const settingsQuery = useInfiniteQuery({
    queryKey: queryKeys.suggest.settings(workspaceId, projectId ?? ''),
    queryFn: ({ pageParam }) =>
      listSuggestSettings({
        workspaceId,
        projectId: projectId!,
        cursor: pageParam,
        limit: SUGGEST_PAGE_SIZE,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => (last.hasMore && last.nextCursor ? last.nextCursor : undefined),
    enabled: canView && Boolean(projectId),
  });

  const channelsQuery = useQuery({
    queryKey: queryKeys.channels.list(workspaceId),
    queryFn: () => listChannels({ workspaceId, limit: 100 }),
    enabled: canView,
  });

  const settings = useMemo(
    () =>
      (settingsQuery.data?.pages ?? [])
        .flatMap((page) => page.items ?? [])
        .filter((item) => !item.workspaceId || item.workspaceId === workspaceId),
    [settingsQuery.data?.pages, workspaceId],
  );
  const channels = useMemo(
    () => channelsQuery.data?.items ?? [],
    [channelsQuery.data?.items],
  );
  const channelsById = useMemo(() => {
    const map = new Map<string, Channel>();
    for (const channel of channels) {
      if (channel.id) map.set(channel.id, channel);
    }
    return map;
  }, [channels]);

  const vkChannels = channels.filter((channel) => channel.network === 'vk');
  const connectedIds = new Set(settings.map((item) => item.channelId).filter(Boolean));
  const availableVk = vkChannels.filter(
    (channel) => channel.id && !connectedIds.has(channel.id) && channel.status === 'active',
  );
  const atSoftLimit = settings.length >= SUGGEST_SOFT_CHANNEL_LIMIT;

  const settingsIds = settings
    .map((item) => item.id)
    .filter((id): id is string => Boolean(id))
    .join(',');

  const pendingQuery = useQuery({
    queryKey: queryKeys.suggest.all(workspaceId).concat(['pending-counts', settingsIds]),
    queryFn: async () => {
      const counts: Record<string, { count: number; hasMore: boolean }> = {};
      await Promise.all(
        settings.map(async (item) => {
          if (!item.id) return;
          const page = await listSuggestItems({
            settingsId: item.id,
            status: 'pending_moderation',
            limit: 100,
          });
          const visible = (page.items ?? []).filter(
            (entry) => !entry.workspaceId || entry.workspaceId === workspaceId,
          );
          counts[item.id] = {
            count: visible.length,
            hasMore: Boolean(page.hasMore),
          };
        }),
      );
      return counts;
    },
    enabled: canView && settings.length > 0,
  });

  const connectMutation = useMutation({
    mutationFn: async (channelId: string) => {
      if (!projectId) throw new Error('missing project');
      return createSuggestSettings(workspaceId, projectId, {
        channelId,
        mode: 'manual',
        config: configAsApiBlob(withConfigDefaults({})),
      });
    },
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.suggest.all(workspaceId) });
      setPickerOpen(false);
      setSelectedChannelId(null);
      toast({ title: t('connectSuccess') });
      // Navigate via soft link preference — caller can open detail.
      void created;
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('connectError'),
        description: getErrorMessage(error),
      });
    },
  });

  const firstChannelId = settings[0]?.channelId ?? vkChannels[0]?.id;

  if (!canView) {
    return (
      <EmptyState
        icon={<ShieldOff className="h-6 w-6" aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
      />
    );
  }

  if (projectsQuery.isError || settingsQuery.isError || channelsQuery.isError) {
    const error = projectsQuery.error ?? settingsQuery.error ?? channelsQuery.error;
    return (
      <EmptyState
        role="alert"
        title={t('loadErrorTitle')}
        description={getErrorMessage(error)}
        action={
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              void projectsQuery.refetch();
              void settingsQuery.refetch();
              void channelsQuery.refetch();
            }}
          >
            {t('retry')}
          </Button>
        }
      />
    );
  }

  if (projectsQuery.isLoading || settingsQuery.isLoading || channelsQuery.isLoading) {
    return (
      <div className="space-y-4" data-testid="suggest-loading">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6" data-testid="suggest-page">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{t('eyebrow')}</p>
          <h1 className="text-2xl font-semibold tracking-tight">{t('title')}</h1>
          <p className="mt-1 max-w-2xl text-sm text-muted-foreground">{t('description')}</p>
        </div>
        {canManage && !atSoftLimit && availableVk.length > 0 ? (
          <Button
            data-testid="suggest-connect-channel"
            onClick={() => setPickerOpen((open) => !open)}
          >
            {t('connectChannel')}
          </Button>
        ) : canManage && availableVk.length === 0 && vkChannels.length === 0 ? null : canManage &&
          !atSoftLimit ? (
          <Button asChild data-testid="suggest-create-settings">
            <Link href={`/app/${workspaceId}/automations/suggest/new`}>{t('createSettings')}</Link>
          </Button>
        ) : null}
      </header>

      {atSoftLimit ? (
        <PlanLimitPaywall
          workspaceId={workspaceId}
          limitKey="grabber_rules"
          used={settings.length}
          limit={SUGGEST_SOFT_CHANNEL_LIMIT}
        />
      ) : null}

      {vkChannels.length === 0 ? (
        <EmptyState
          icon={<Radio className="h-6 w-6" aria-hidden />}
          title={t('emptyVkTitle')}
          description={t('emptyVkBody')}
        />
      ) : null}

      {pickerOpen && canManage ? (
        <section
          className="space-y-3 rounded-md border border-border p-4"
          data-testid="suggest-connect-picker"
        >
          <h2 className="text-sm font-medium">{t('pickChannel')}</h2>
          <AutomationChannelPicker
            channels={availableVk}
            networks={['vk']}
            value={selectedChannelId}
            onChange={setSelectedChannelId}
            isChannelCapable={channelHasPublishSuggested}
          />
          {selectedChannelId &&
          !channelHasPublishSuggested(channelsById.get(selectedChannelId)) ? (
            <CapabilityNotice variant="vk_user_wall" />
          ) : null}
          <Button
            disabled={!selectedChannelId || connectMutation.isPending}
            onClick={() => selectedChannelId && connectMutation.mutate(selectedChannelId)}
            data-testid="suggest-connect-confirm"
          >
            {t('connectConfirm')}
          </Button>
        </section>
      ) : null}

      {firstChannelId ? (
        <div className="space-y-2">
          <h2 className="text-sm font-medium">{t('eventStatusTitle')}</h2>
          <ChannelEventStatus workspaceId={workspaceId} channelId={firstChannelId} />
        </div>
      ) : null}

      <section aria-label={t('listTitle')} data-testid="suggest-settings-list">
        <div className="mb-3">
          <h2 className="text-lg font-semibold">{t('listTitle')}</h2>
          <p className="text-sm text-muted-foreground">{t('listHint')}</p>
        </div>
        {settings.length === 0 ? (
          <EmptyState
            icon={<Workflow className="h-6 w-6" aria-hidden />}
            title={t('emptySettingsTitle')}
            description={t('emptySettingsBody')}
          />
        ) : (
          <div>
            {settings.map((item: SuggestSettings) => (
              <SettingsCard
                key={item.id}
                settings={item}
                workspaceId={workspaceId}
                channel={item.channelId ? channelsById.get(item.channelId) : undefined}
                pendingCount={pendingQuery.data?.[item.id!]?.count ?? 0}
                pendingHasMore={pendingQuery.data?.[item.id!]?.hasMore ?? false}
                canManage={canManage}
              />
            ))}
            {settingsQuery.hasNextPage ? (
              <div className="pt-3">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={settingsQuery.isFetchingNextPage}
                  onClick={() => settingsQuery.fetchNextPage()}
                  data-testid="suggest-settings-load-more"
                >
                  {t('loadMore')}
                </Button>
              </div>
            ) : null}
          </div>
        )}
      </section>
    </div>
  );
}
