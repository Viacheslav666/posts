'use client';

import type { Channel } from '@posts/api-client';
import { NetworkIcon, cn } from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useMemo, useState } from 'react';

import { toSocialNetwork } from '@/features/channels/model/network-map';

export type AutomationChannelPickerProps = {
  channels: Channel[];
  value?: string | null;
  onChange: (channelId: string) => void;
  /** When set, only these networks are selectable; others are disabled with a reason. */
  networks?: Array<'vk' | 'tg' | 'ok' | 'max' | 'rutube'>;
  /**
   * Optional capability gate. Return false to disable a channel.
   * OpenAPI FeatureFlags do not yet include suggest/antispam keys — callers pass custom checks.
   */
  isChannelCapable?: (channel: Channel) => boolean;
  disabledReason?: (channel: Channel) => string | undefined;
  disabled?: boolean;
  className?: string;
  id?: string;
};

export function AutomationChannelPicker({
  channels,
  value,
  onChange,
  networks,
  isChannelCapable,
  disabledReason,
  disabled,
  className,
  id = 'automation-channel-picker',
}: AutomationChannelPickerProps) {
  const t = useTranslations('automations.common.channelPicker');
  const [filter, setFilter] = useState('');

  const filtered = useMemo(() => {
    const q = filter.trim().toLocaleLowerCase('ru');
    return channels.filter((channel) => {
      if (networks && channel.network && !networks.includes(channel.network)) {
        return true; // still listed but disabled below
      }
      if (!q) return true;
      const hay = `${channel.title ?? ''} ${channel.username ?? ''} ${channel.network ?? ''}`.toLocaleLowerCase(
        'ru',
      );
      return hay.includes(q);
    });
  }, [channels, filter, networks]);

  return (
    <div className={cn('space-y-2', className)} data-testid="automation-channel-picker">
      <label htmlFor={`${id}-search`} className="text-sm font-medium">
        {t('label')}
      </label>
      <input
        id={`${id}-search`}
        type="search"
        value={filter}
        disabled={disabled}
        placeholder={t('search')}
        className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        onChange={(event) => setFilter(event.target.value)}
      />

      <ul
        role="listbox"
        aria-label={t('listLabel')}
        className="max-h-64 divide-y divide-border overflow-y-auto rounded-md border border-border"
      >
        {filtered.length === 0 ? (
          <li className="px-3 py-4 text-sm text-muted-foreground">{t('empty')}</li>
        ) : null}
        {filtered.map((channel) => {
          const networkOk =
            !networks || !channel.network || networks.includes(channel.network);
          const capable = isChannelCapable ? isChannelCapable(channel) : true;
          const statusOk = channel.status === 'active';
          const enabled = networkOk && capable && statusOk && !disabled;
          const reason =
            disabledReason?.(channel) ??
            (!networkOk
              ? t('reasons.network')
              : !statusOk
                ? t('reasons.status', { status: channel.status ?? 'unknown' })
                : !capable
                  ? t('reasons.capability')
                  : undefined);
          const selected = value === channel.id;

          return (
            <li key={channel.id}>
              <button
                type="button"
                role="option"
                aria-selected={selected}
                aria-disabled={!enabled}
                disabled={!enabled}
                title={reason}
                data-testid={`automation-channel-${channel.id}`}
                className={cn(
                  'flex w-full items-start gap-3 px-3 py-2.5 text-left text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring',
                  selected && 'bg-primary/10',
                  enabled ? 'hover:bg-muted/60' : 'cursor-not-allowed opacity-60',
                )}
                onClick={() => {
                  if (enabled && channel.id) onChange(channel.id);
                }}
              >
                {(() => {
                  const social = toSocialNetwork(channel.network ?? '');
                  return social ? (
                    <NetworkIcon
                      network={social}
                      size="sm"
                      className="mt-0.5 shrink-0"
                      aria-hidden
                    />
                  ) : (
                    <span className="mt-0.5 h-6 w-6 shrink-0 rounded-md bg-muted" aria-hidden />
                  );
                })()}
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium">
                    {channel.title ?? channel.username ?? channel.id}
                  </span>
                  {!enabled && reason ? (
                    <span className="mt-0.5 block text-xs text-muted-foreground">{reason}</span>
                  ) : null}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
