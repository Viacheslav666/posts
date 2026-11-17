'use client';

import type { Channel, ChannelSet } from '@posts/api-client';
import { Button, EmptyState, Input, Skeleton, toast } from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FolderKanban } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import {
  createChannelSet,
  deleteChannelSet,
  listChannelSets,
} from '../api/channels-api';

type ChannelSetsPanelProps = {
  workspaceId: string;
  channels: Channel[];
  canManage: boolean;
};

export function ChannelSetsPanel({ workspaceId, channels, canManage }: ChannelSetsPanelProps) {
  const t = useTranslations('channels');
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [selected, setSelected] = useState<string[]>([]);

  const setsQuery = useQuery({
    queryKey: queryKeys.channels.sets(workspaceId),
    queryFn: () => listChannelSets(workspaceId),
  });

  const channelTitle = useMemo(() => {
    const map = new Map<string, string>();
    for (const channel of channels) {
      if (channel.id) {
        map.set(channel.id, channel.title ?? channel.username ?? channel.id);
      }
    }
    return map;
  }, [channels]);

  const createMutation = useMutation({
    mutationFn: () =>
      createChannelSet(workspaceId, {
        name: name.trim(),
        channelIds: selected,
      }),
    onSuccess: async () => {
      setName('');
      setSelected([]);
      await queryClient.invalidateQueries({ queryKey: queryKeys.channels.sets(workspaceId) });
      toast({ title: t('sets.createdTitle') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('sets.errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (setId: string) => deleteChannelSet(setId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.channels.sets(workspaceId) });
      toast({ title: t('sets.deletedTitle') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('sets.errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  const toggle = (id: string) => {
    setSelected((current) =>
      current.includes(id) ? current.filter((item) => item !== id) : [...current, id],
    );
  };

  if (setsQuery.isLoading) {
    return <Skeleton className="h-40 w-full" />;
  }

  const sets: ChannelSet[] = setsQuery.data?.items ?? [];

  return (
    <section className="space-y-4" data-testid="channel-sets-panel" aria-label={t('sets.title')}>
      <div>
        <h2 className="text-lg font-semibold">{t('sets.title')}</h2>
        <p className="text-sm text-muted-foreground">{t('sets.description')}</p>
      </div>

      {canManage ? (
        <div className="space-y-3 rounded-lg border border-border p-4">
          <label className="text-sm font-medium" htmlFor="channel-set-name">
            {t('sets.nameLabel')}
          </label>
          <Input
            id="channel-set-name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder={t('sets.namePlaceholder')}
            data-testid="channel-set-name"
          />
          <fieldset>
            <legend className="mb-2 text-sm font-medium">{t('sets.channelsLabel')}</legend>
            <ul className="max-h-40 space-y-1 overflow-y-auto">
              {channels.map((channel) => (
                <li key={channel.id}>
                  <label className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 hover:bg-muted/60">
                    <input
                      type="checkbox"
                      checked={selected.includes(channel.id!)}
                      onChange={() => toggle(channel.id!)}
                      disabled={selected.length >= 50 && !selected.includes(channel.id!)}
                    />
                    <span className="truncate text-sm">
                      {channel.title ?? channel.username ?? channel.id}
                    </span>
                  </label>
                </li>
              ))}
            </ul>
            <p className="mt-1 text-xs text-muted-foreground">
              {t('sets.selected', { count: selected.length })}
            </p>
          </fieldset>
          <Button
            type="button"
            onClick={() => createMutation.mutate()}
            disabled={createMutation.isPending || name.trim().length < 1 || selected.length < 1}
            data-testid="channel-set-create"
          >
            {t('sets.create')}
          </Button>
        </div>
      ) : null}

      {sets.length === 0 ? (
        <EmptyState
          icon={<FolderKanban className="h-6 w-6" aria-hidden />}
          title={t('sets.emptyTitle')}
          description={t('sets.emptyBody')}
        />
      ) : (
        <ul className="space-y-2">
          {sets.map((set) => (
            <li
              key={set.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border px-4 py-3"
              data-testid={`channel-set-${set.id}`}
            >
              <div className="min-w-0">
                <p className="font-medium">{set.name}</p>
                <p className="truncate text-sm text-muted-foreground">
                  {(set.channelIds ?? [])
                    .map((id) => channelTitle.get(id) ?? id)
                    .join(', ') || t('sets.noChannels')}
                </p>
              </div>
              {canManage ? (
                <Button
                  type="button"
                  size="sm"
                  variant="destructive"
                  onClick={() => deleteMutation.mutate(set.id!)}
                >
                  {t('sets.delete')}
                </Button>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
