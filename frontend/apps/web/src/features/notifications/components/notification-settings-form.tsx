'use client';

import type { ChannelPreference, NotificationSettings } from '@posts/api-client';
import { Button, EmptyState, Skeleton, toast } from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ShieldOff } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';

import { SettingsShell } from '@/features/settings/components/settings-shell';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';
import { getErrorMessage } from '@/shared/lib/errors';

import {
  getNotificationSettings,
  startTelegramLink,
  unlinkTelegram,
  updateNotificationSettings,
} from '../api/notifications-api';
import { disableWebPush, enableWebPush, isWebPushSupported } from '../lib/web-push';
import { DEFAULT_PREFERENCE_MATRIX } from '../model/defaults';
import {
  DELIVERY_CHANNELS,
  NOTIFICATION_EVENT_TYPES,
  type DeliveryChannel,
  type NotificationEventType,
} from '../model/types';

type NotificationSettingsFormProps = {
  workspaceId: string;
};

function Switch({
  checked,
  onCheckedChange,
  label,
  disabled,
  id,
}: {
  checked: boolean;
  onCheckedChange: (next: boolean) => void;
  label: string;
  disabled?: boolean;
  id: string;
}) {
  return (
    <button
      type="button"
      id={id}
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onCheckedChange(!checked)}
      className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50 ${
        checked ? 'border-primary bg-primary' : 'border-border bg-muted'
      }`}
    >
      <span
        className={`inline-block h-4 w-4 transform rounded-full bg-background transition-transform ${
          checked ? 'translate-x-6' : 'translate-x-1'
        }`}
      />
    </button>
  );
}

export function NotificationSettingsForm({ workspaceId }: NotificationSettingsFormProps) {
  const t = useTranslations('settings.notifications');
  const queryClient = useQueryClient();
  const canManage = useCan('notifications.manage_self', workspaceId);
  const [draft, setDraft] = useState<NotificationSettings | null>(null);

  const settingsQuery = useQuery({
    queryKey: queryKeys.notifications.settings(workspaceId),
    queryFn: () => getNotificationSettings(workspaceId),
    enabled: canManage,
  });

  useEffect(() => {
    if (settingsQuery.data) {
      setDraft(settingsQuery.data);
    }
  }, [settingsQuery.data]);

  const saveMutation = useMutation({
    mutationFn: (body: NotificationSettings) =>
      updateNotificationSettings(workspaceId, {
        preferences: body.preferences,
        quietHours: body.quietHours,
        emailEnabled: body.emailEnabled,
        telegramBotEnabled: body.telegramBotEnabled,
        webPushEnabled: body.webPushEnabled,
        inAppEnabled: body.inAppEnabled,
        mutedTypes: body.mutedTypes,
      }),
    onSuccess: async (data) => {
      setDraft(data);
      await queryClient.invalidateQueries({
        queryKey: queryKeys.notifications.settings(workspaceId),
      });
      toast({ title: t('savedTitle'), description: t('savedBody') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error, t('errorTitle')),
      });
    },
  });

  const telegramLinkMutation = useMutation({
    mutationFn: () => startTelegramLink(workspaceId),
    onSuccess: async (data) => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.notifications.settings(workspaceId),
      });
      if (data.deepLink) {
        window.open(data.deepLink, '_blank', 'noopener,noreferrer');
      }
      toast({ title: t('telegram.linkStarted'), description: t('telegram.linkHint') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error, t('errorTitle')),
      });
    },
  });

  const telegramUnlinkMutation = useMutation({
    mutationFn: () => unlinkTelegram(workspaceId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.notifications.settings(workspaceId),
      });
      toast({ title: t('telegram.unlinked') });
    },
  });

  const webPushMutation = useMutation({
    mutationFn: async (enable: boolean) => {
      if (enable) {
        return enableWebPush(workspaceId);
      }
      await disableWebPush(workspaceId);
      return 'granted' as const;
    },
    onSuccess: async (result, enable) => {
      if (enable && result !== 'granted') {
        toast({
          variant: 'destructive',
          title: t('webPush.deniedTitle'),
          description: t('webPush.deniedBody'),
        });
        return;
      }
      await queryClient.invalidateQueries({
        queryKey: queryKeys.notifications.settings(workspaceId),
      });
      toast({
        title: enable ? t('webPush.enabledTitle') : t('webPush.disabledTitle'),
      });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error, t('errorTitle')),
      });
    },
  });

  if (!canManage) {
    return (
      <SettingsShell workspaceId={workspaceId} title={t('title')} description={t('description')}>
        <EmptyState
          icon={<ShieldOff className="h-6 w-6" aria-hidden />}
          title={t('forbiddenTitle')}
          description={t('forbiddenBody')}
        />
      </SettingsShell>
    );
  }

  if (settingsQuery.isLoading || !draft) {
    return (
      <SettingsShell workspaceId={workspaceId} title={t('title')} description={t('description')}>
        <Skeleton className="h-40 w-full" />
      </SettingsShell>
    );
  }

  const eventLabel = (type: NotificationEventType): string => {
    const map: Record<NotificationEventType, string> = {
      'publication.failed': t('events.publication.failed'),
      'publication.succeeded': t('events.publication.succeeded'),
      'channel.reauth_required': t('events.channel.reauth_required'),
      'channel.no_permissions': t('events.channel.no_permissions'),
      'grabber.moderation_queued': t('events.grabber.moderation_queued'),
      'grabber.item_failed': t('events.grabber.item_failed'),
      'ord.error': t('events.ord.error'),
      'ord.erid_failed': t('events.ord.erid_failed'),
      'invitation.created': t('events.invitation.created'),
      'team.approval_requested': t('events.team.approval_requested'),
    };
    return map[type];
  };

  const preferenceFor = (type: NotificationEventType): ChannelPreference => {
    return (
      draft.preferences?.[type] ??
      DEFAULT_PREFERENCE_MATRIX[type] ?? {
        email: true,
        telegram: true,
        webPush: true,
        inApp: true,
      }
    );
  };

  const setPreference = (
    type: NotificationEventType,
    channel: DeliveryChannel,
    value: boolean,
  ) => {
    const current = preferenceFor(type);
    setDraft({
      ...draft,
      preferences: {
        ...draft.preferences,
        [type]: { ...current, [channel]: value },
      },
    });
  };

  return (
    <SettingsShell workspaceId={workspaceId} title={t('title')} description={t('description')}>
      <section className="space-y-3" data-testid="notification-masters">
        <h2 className="text-lg font-medium">{t('mastersTitle')}</h2>
        {(
          [
            ['emailEnabled', 'email'],
            ['telegramBotEnabled', 'telegram'],
            ['webPushEnabled', 'webPush'],
            ['inAppEnabled', 'inApp'],
          ] as const
        ).map(([field, channel]) => (
          <div key={field} className="flex items-center justify-between gap-3">
            <label className="text-sm" htmlFor={`master-${field}`}>
              {t(`channels.${channel}`)}
            </label>
            <Switch
              id={`master-${field}`}
              label={t(`channels.${channel}`)}
              checked={Boolean(draft[field])}
              onCheckedChange={(next) => setDraft({ ...draft, [field]: next })}
            />
          </div>
        ))}
      </section>

      <section className="space-y-3 border-t border-border pt-6" data-testid="notification-matrix">
        <h2 className="text-lg font-medium">{t('matrixTitle')}</h2>
        <p className="text-sm text-muted-foreground">{t('matrixHint')}</p>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[36rem] border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left">
                <th className="py-2 pr-2 font-medium">{t('eventColumn')}</th>
                {DELIVERY_CHANNELS.map((channel) => (
                  <th key={channel} className="px-2 py-2 font-medium">
                    {t(`channels.${channel}`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {NOTIFICATION_EVENT_TYPES.map((type) => {
                const pref = preferenceFor(type);
                return (
                  <tr key={type} className="border-b border-border/70">
                    <td className="py-2 pr-2">{eventLabel(type)}</td>
                    {DELIVERY_CHANNELS.map((channel) => (
                      <td key={channel} className="px-2 py-2">
                        <input
                          type="checkbox"
                          className="h-4 w-4 accent-primary"
                          aria-label={`${eventLabel(type)} - ${t(`channels.${channel}`)}`}
                          checked={Boolean(pref[channel])}
                          onChange={(e) => setPreference(type, channel, e.target.checked)}
                        />
                      </td>
                    ))}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>

      <section className="space-y-3 border-t border-border pt-6" data-testid="notification-telegram">
        <h2 className="text-lg font-medium">{t('telegram.title')}</h2>
        {draft.telegram?.linked ? (
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm text-muted-foreground">
              {t('telegram.linkedAs', { username: draft.telegram.username ?? '-' })}
            </p>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => telegramUnlinkMutation.mutate()}
              disabled={telegramUnlinkMutation.isPending}
            >
              {t('telegram.unlink')}
            </Button>
          </div>
        ) : (
          <Button
            type="button"
            variant="secondary"
            onClick={() => telegramLinkMutation.mutate()}
            disabled={telegramLinkMutation.isPending}
            data-testid="telegram-link-start"
          >
            {t('telegram.link')}
          </Button>
        )}
      </section>

      <section className="space-y-3 border-t border-border pt-6" data-testid="notification-webpush">
        <h2 className="text-lg font-medium">{t('webPush.title')}</h2>
        <p className="text-sm text-muted-foreground">
          {isWebPushSupported() ? t('webPush.supported') : t('webPush.unsupported')}
        </p>
        <p className="text-sm text-muted-foreground">
          {t('webPush.subscriptions', { count: draft.webPushSubscriptionCount ?? 0 })}
        </p>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant="secondary"
            disabled={!isWebPushSupported() || webPushMutation.isPending}
            onClick={() => webPushMutation.mutate(true)}
            data-testid="webpush-enable"
          >
            {t('webPush.enable')}
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={!isWebPushSupported() || webPushMutation.isPending}
            onClick={() => webPushMutation.mutate(false)}
          >
            {t('webPush.disable')}
          </Button>
        </div>
      </section>

      <section className="space-y-3 border-t border-border pt-6" data-testid="notification-quiet-hours">
        <h2 className="text-lg font-medium">{t('quietHours.title')}</h2>
        <div className="flex items-center justify-between gap-3">
          <label className="text-sm" htmlFor="quiet-enabled">
            {t('quietHours.enabled')}
          </label>
          <Switch
            id="quiet-enabled"
            label={t('quietHours.enabled')}
            checked={Boolean(draft.quietHours?.enabled)}
            onCheckedChange={(next) =>
              setDraft({
                ...draft,
                quietHours: { ...draft.quietHours, enabled: next },
              })
            }
          />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="space-y-1 text-sm">
            <span>{t('quietHours.start')}</span>
            <input
              type="time"
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              value={draft.quietHours?.start ?? '22:00'}
              onChange={(e) =>
                setDraft({
                  ...draft,
                  quietHours: { ...draft.quietHours, start: e.target.value },
                })
              }
            />
          </label>
          <label className="space-y-1 text-sm">
            <span>{t('quietHours.end')}</span>
            <input
              type="time"
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              value={draft.quietHours?.end ?? '08:00'}
              onChange={(e) =>
                setDraft({
                  ...draft,
                  quietHours: { ...draft.quietHours, end: e.target.value },
                })
              }
            />
          </label>
        </div>
      </section>

      <Button
        type="button"
        onClick={() => saveMutation.mutate(draft)}
        disabled={saveMutation.isPending}
        data-testid="notification-settings-save"
      >
        {saveMutation.isPending ? t('saving') : t('save')}
      </Button>
    </SettingsShell>
  );
}
