'use client';

import { Badge, Button, NetworkIcon, type SocialNetwork } from '@posts/ui';
import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import type { EditorChannelOption } from '@/features/channels/hooks/use-workspace-channels';
import { useCan } from '@/shared/hooks/use-can';

import {
  isStorySupportedNetwork,
  isStoryUnsupportedVisibleNetwork,
  isVkStoryReady,
} from '../model/networks';
import { StoryConnectButton } from './story-connect-button';

const networkToIcon: Record<string, SocialNetwork | null> = {
  vk: 'vk',
  tg: 'telegram',
  ok: 'ok',
  max: 'max',
  rutube: 'rutube',
};

type StoryChannelPickerProps = {
  workspaceId: string;
  channels: EditorChannelOption[];
  selectedIds: string[];
  onChange: (ids: string[]) => void;
  loading?: boolean;
  onConnected?: () => void;
};

export function StoryChannelPicker({
  workspaceId,
  channels,
  selectedIds,
  onChange,
  loading = false,
  onConnected,
}: StoryChannelPickerProps) {
  const t = useTranslations('stories.channels');
  const canManage = useCan('channels.manage', workspaceId);
  const [query, setQuery] = useState('');

  const { selectable, unsupportedVisible } = useMemo(() => {
    const q = query.trim().toLowerCase();
    const match = (channel: EditorChannelOption) =>
      !q ||
      channel.name.toLowerCase().includes(q) ||
      channel.projectName.toLowerCase().includes(q) ||
      channel.network.includes(q);

    const selectableChannels = channels.filter(
      (channel) => isStorySupportedNetwork(channel.network) && match(channel),
    );
    const unsupported = channels.filter(
      (channel) => isStoryUnsupportedVisibleNetwork(channel.network) && match(channel),
    );
    return { selectable: selectableChannels, unsupportedVisible: unsupported };
  }, [channels, query]);

  const grouped = useMemo(() => {
    const map = new Map<string, EditorChannelOption[]>();
    for (const channel of selectable) {
      const list = map.get(channel.projectName) ?? [];
      list.push(channel);
      map.set(channel.projectName, list);
    }
    return [...map.entries()];
  }, [selectable]);

  const toggle = (channel: EditorChannelOption) => {
    if (!isVkStoryReady(channel.network, channel.storyConnectionStatus)) {
      return;
    }
    if (selectedIds.includes(channel.id)) {
      onChange(selectedIds.filter((id) => id !== channel.id));
    } else {
      onChange([...selectedIds, channel.id]);
    }
  };

  return (
    <section aria-label={t('label')} className="flex flex-col gap-3" data-testid="story-channel-picker">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground">{t('title')}</h2>
        <Badge variant="draft">{t('selected', { count: selectedIds.length })}</Badge>
      </div>
      <p className="text-xs text-muted-foreground">{t('hint')}</p>
      <label className="sr-only" htmlFor="story-channel-search">
        {t('search')}
      </label>
      <input
        id="story-channel-search"
        type="search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder={t('search')}
        className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm outline-none ring-offset-background placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring"
      />

      {loading ? (
        <p className="text-sm text-muted-foreground">{t('loading')}</p>
      ) : (
        <div className="max-h-72 space-y-3 overflow-y-auto pr-1">
          {grouped.map(([project, items]) => (
            <div key={project}>
              <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {project}
              </p>
              <ul className="space-y-1">
                {items.map((channel) => {
                  const ready = isVkStoryReady(channel.network, channel.storyConnectionStatus);
                  const checked = selectedIds.includes(channel.id);
                  const icon = networkToIcon[channel.network];
                  return (
                    <li key={channel.id}>
                      <div
                        className={`flex items-center gap-2 rounded-md px-2 py-1.5 ${
                          ready ? 'hover:bg-muted/60' : 'opacity-80'
                        }`}
                        data-testid={`story-channel-${channel.id}`}
                        data-story-ready={ready ? 'true' : 'false'}
                      >
                        <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-2">
                          <input
                            type="checkbox"
                            checked={checked}
                            disabled={!ready}
                            onChange={() => toggle(channel)}
                            className="h-4 w-4 accent-primary"
                            aria-label={channel.name}
                          />
                          {icon ? <NetworkIcon network={icon} size="sm" /> : null}
                          <span className="truncate text-sm">{channel.name}</span>
                        </label>
                        {!ready && channel.network === 'vk' ? (
                          <div className="flex shrink-0 items-center gap-2">
                            <span className="text-xs text-amber-700 dark:text-amber-400">
                              {t('needsConnect')}
                            </span>
                            {canManage ? (
                              <StoryConnectButton
                                channelId={channel.id}
                                workspaceId={workspaceId}
                                onConnected={onConnected}
                              />
                            ) : (
                              <Button type="button" size="sm" variant="outline" disabled>
                                {t('connect')}
                              </Button>
                            )}
                          </div>
                        ) : null}
                      </div>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}

          {unsupportedVisible.length > 0 ? (
            <div data-testid="story-unsupported-networks">
              <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t('unsupportedTitle')}
              </p>
              <ul className="space-y-1">
                {unsupportedVisible.map((channel) => {
                  const icon = networkToIcon[channel.network];
                  const reason =
                    channel.network === 'tg' ? t('unsupportedTg') : t('unsupportedMax');
                  return (
                    <li
                      key={channel.id}
                      className="flex items-center gap-2 rounded-md bg-muted/40 px-2 py-1.5"
                      data-testid={`story-unsupported-${channel.network}`}
                      data-network={channel.network}
                    >
                      <input
                        type="checkbox"
                        disabled
                        className="h-4 w-4"
                        aria-disabled
                        aria-label={`${channel.name}: ${reason}`}
                      />
                      {icon ? <NetworkIcon network={icon} size="sm" /> : null}
                      <div className="min-w-0">
                        <p className="truncate text-sm text-muted-foreground">{channel.name}</p>
                        <p className="text-xs text-muted-foreground">{reason}</p>
                      </div>
                    </li>
                  );
                })}
              </ul>
            </div>
          ) : null}

          {/* Instagram must never appear (Q-STORY-05) - assert via e2e absence. */}
          <div className="sr-only" data-testid="story-ig-absent" aria-hidden>
            ig-absent
          </div>

          {selectable.length === 0 && unsupportedVisible.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t('empty')}</p>
          ) : null}
        </div>
      )}
    </section>
  );
}
