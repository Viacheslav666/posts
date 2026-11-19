'use client';

import type { Channel, SuggestSettings } from '@posts/api-client';
import { Button, cn } from '@posts/ui';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

import { CapabilityNotice, ChannelEventStatus } from '@/features/automations-common';

import { channelHasPublishSuggested } from '../model/capabilities';
import { SettingsStatusBadge } from './settings-status-badge';

type SettingsCardProps = {
  settings: SuggestSettings;
  workspaceId: string;
  channel?: Channel;
  pendingCount: number;
  canManage: boolean;
};

export function SettingsCard({
  settings,
  workspaceId,
  channel,
  pendingCount,
  canManage,
}: SettingsCardProps) {
  const t = useTranslations('automations.suggest');
  const id = settings.id!;
  const capable = channelHasPublishSuggested(channel);

  return (
    <article
      data-testid={`suggest-settings-${id}`}
      className={cn('space-y-3 border-b border-border py-4 last:border-b-0')}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-base font-semibold">
              {channel?.title ?? channel?.username ?? t('unknownChannel')}
            </h3>
            <SettingsStatusBadge status={settings.status} />
            <span className="rounded-md border border-border px-2 py-0.5 text-xs text-muted-foreground">
              {t(`mode.${settings.mode ?? 'manual'}`)}
            </span>
            {pendingCount > 0 ? (
              <span
                className="rounded-md bg-amber-500/15 px-2 py-0.5 text-xs text-amber-800 dark:text-amber-300"
                data-testid={`suggest-pending-count-${id}`}
              >
                {t('pendingCount', { count: pendingCount })}
              </span>
            ) : null}
          </div>
          {channel?.id ? (
            <ChannelEventStatus workspaceId={workspaceId} channelId={channel.id} />
          ) : null}
          {!capable ? <CapabilityNotice variant="vk_user_wall" className="mt-2" /> : null}
        </div>
        <div className="flex flex-wrap gap-2">
          <Button asChild size="sm" variant="outline" data-testid={`suggest-open-${id}`}>
            <Link href={`/app/${workspaceId}/automations/suggest/${id}`}>{t('openSettings')}</Link>
          </Button>
          {canManage && settings.status === 'active' ? (
            <span className="sr-only">{t('status.active')}</span>
          ) : null}
        </div>
      </div>
    </article>
  );
}
