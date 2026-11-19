'use client';

import type { Channel } from '@posts/api-client';
import { NetworkIcon, cn } from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useMemo, useState } from 'react';

import { toSocialNetwork } from '@/features/channels/model/network-map';

import type { TargetNetwork } from '../model/types';
import { TARGET_NETWORKS } from '../model/types';

export type TargetMultiPickerProps = {
  channels: Channel[];
  value: string[];
  onChange: (ids: string[]) => void;
  /** Source channel id — excluded from selection. */
  excludeChannelId?: string | null;
  networks?: TargetNetwork[];
  disabled?: boolean;
  className?: string;
  error?: string;
};

export function TargetMultiPicker({
  channels,
  value,
  onChange,
  excludeChannelId,
  networks = TARGET_NETWORKS,
  disabled,
  className,
  error,
}: TargetMultiPickerProps) {
  const t = useTranslations('automations.crosspost.targets');
  const [filter, setFilter] = useState('');

  const filtered = useMemo(() => {
    const q = filter.trim().toLocaleLowerCase('ru');
    return channels.filter((channel) => {
      if (channel.id === excludeChannelId) return false;
      if (channel.network && !networks.includes(channel.network as TargetNetwork)) return false;
      if (!q) return true;
      const hay = `${channel.title ?? ''} ${channel.username ?? ''} ${channel.network ?? ''}`.toLocaleLowerCase(
        'ru',
      );
      return hay.includes(q);
    });
  }, [channels, excludeChannelId, filter, networks]);

  const toggle = (channelId: string) => {
    if (value.includes(channelId)) {
      onChange(value.filter((id) => id !== channelId));
    } else {
      onChange([...value, channelId]);
    }
  };

  return (
    <div className={cn('space-y-2', className)} data-testid="crosspost-target-picker">
      <label htmlFor="crosspost-target-search" className="text-sm font-medium">
        {t('label')}
      </label>
      <input
        id="crosspost-target-search"
        type="search"
        value={filter}
        disabled={disabled}
        placeholder={t('search')}
        className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        onChange={(event) => setFilter(event.target.value)}
      />
      <ul
        role="listbox"
        aria-multiselectable
        aria-label={t('listLabel')}
        className="max-h-64 divide-y divide-border overflow-y-auto rounded-md border border-border"
      >
        {filtered.length === 0 ? (
          <li className="px-3 py-4 text-sm text-muted-foreground">{t('empty')}</li>
        ) : null}
        {filtered.map((channel) => {
          const selected = value.includes(channel.id!);
          const iconNetwork = toSocialNetwork(channel.network ?? '');
          const unavailable = channel.status !== 'active';
          return (
            <li key={channel.id}>
              <button
                type="button"
                role="option"
                aria-selected={selected}
                disabled={disabled || unavailable}
                data-testid={`crosspost-target-${channel.id}`}
                className={cn(
                  'flex w-full items-center gap-3 px-3 py-2.5 text-left text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  selected ? 'bg-primary/10' : 'hover:bg-muted/60',
                  unavailable && 'opacity-50',
                )}
                onClick={() => channel.id && toggle(channel.id)}
              >
                <input
                  type="checkbox"
                  checked={selected}
                  readOnly
                  tabIndex={-1}
                  className="h-4 w-4"
                  aria-hidden
                />
                {iconNetwork ? <NetworkIcon network={iconNetwork} size="sm" /> : null}
                <span className="min-w-0 flex-1 truncate font-medium">
                  {channel.title ?? channel.username ?? channel.id}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
      {error ? (
        <p role="alert" className="text-xs text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}
