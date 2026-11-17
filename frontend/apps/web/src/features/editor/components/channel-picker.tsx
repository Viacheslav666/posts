'use client';

import { Badge, NetworkIcon, type SocialNetwork } from '@posts/ui';
import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import type { EditorChannel } from '../model/types';

const networkToIcon: Record<EditorChannel['network'], SocialNetwork | null> = {
  vk: 'vk',
  tg: 'telegram',
  ok: 'ok',
  max: 'max',
  rutube: 'rutube',
};

type ChannelPickerProps = {
  channels: EditorChannel[];
  selectedIds: string[];
  onChange: (ids: string[]) => void;
  loading?: boolean;
};

export function ChannelPicker({
  channels,
  selectedIds,
  onChange,
  loading = false,
}: ChannelPickerProps) {
  const t = useTranslations('editor.channels');
  const [query, setQuery] = useState('');

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) {
      return channels;
    }
    return channels.filter(
      (channel) =>
        channel.name.toLowerCase().includes(q) ||
        channel.projectName.toLowerCase().includes(q) ||
        channel.network.includes(q),
    );
  }, [channels, query]);

  const grouped = useMemo(() => {
    const map = new Map<string, EditorChannel[]>();
    for (const channel of filtered) {
      const list = map.get(channel.projectName) ?? [];
      list.push(channel);
      map.set(channel.projectName, list);
    }
    return [...map.entries()];
  }, [filtered]);

  const toggle = (id: string) => {
    if (selectedIds.includes(id)) {
      onChange(selectedIds.filter((item) => item !== id));
    } else {
      onChange([...selectedIds, id]);
    }
  };

  return (
    <section aria-label={t('label')} className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground">{t('title')}</h2>
        <Badge variant="draft">
          {t('selected', { count: selectedIds.length })}
        </Badge>
      </div>
      <label className="sr-only" htmlFor="channel-search">
        {t('search')}
      </label>
      <input
        id="channel-search"
        type="search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder={t('search')}
        className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm outline-none ring-offset-background placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring"
      />
      {loading ? (
        <p className="text-sm text-muted-foreground">{t('loading')}</p>
      ) : (
        <div className="max-h-64 space-y-3 overflow-y-auto pr-1">
          {grouped.map(([project, items]) => (
            <div key={project}>
              <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {project}
              </p>
              <ul className="space-y-1">
                {items.map((channel) => {
                  const checked = selectedIds.includes(channel.id);
                  const icon = networkToIcon[channel.network];
                  return (
                    <li key={channel.id}>
                      <label className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 hover:bg-muted/60">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => toggle(channel.id)}
                          className="h-4 w-4 accent-primary"
                        />
                        {icon ? <NetworkIcon network={icon} size="sm" /> : null}
                        <span className="truncate text-sm">{channel.name}</span>
                      </label>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
          {filtered.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t('empty')}</p>
          ) : null}
        </div>
      )}
    </section>
  );
}
