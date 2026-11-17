'use client';

import type { Channel, Project } from '@posts/api-client';
import {
  Button,
  ChannelStatusBadge,
  NetworkIcon,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  toast,
  type ChannelConnectionStatus,
} from '@posts/ui';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import { checkChannel, deleteChannel, updateChannel } from '../api/channels-api';
import { toSocialNetwork } from '../model/network-map';
import type { ConnectableNetwork } from '../model/types';

type ChannelCardProps = {
  channel: Channel;
  workspaceId: string;
  projects: Project[];
  canManage: boolean;
  onReconnect: (network: ConnectableNetwork) => void;
};

function isChannelStatus(value: string | undefined): value is ChannelConnectionStatus {
  return (
    value === 'active' ||
    value === 'reauth_required' ||
    value === 'no_permissions' ||
    value === 'disabled'
  );
}

export function ChannelCard({
  channel,
  workspaceId,
  projects,
  canManage,
  onReconnect,
}: ChannelCardProps) {
  const t = useTranslations('channels');
  const queryClient = useQueryClient();
  const icon = toSocialNetwork(channel.network ?? '');
  const status: ChannelConnectionStatus = isChannelStatus(channel.status)
    ? channel.status
    : 'disabled';

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: queryKeys.channels.all(workspaceId) });
  };

  const checkMutation = useMutation({
    mutationFn: () => checkChannel(channel.id!),
    onSuccess: async () => {
      await invalidate();
      toast({ title: t('actions.checkSuccess') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('actions.errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  const disconnectMutation = useMutation({
    mutationFn: () => deleteChannel(channel.id!),
    onSuccess: async () => {
      await invalidate();
      toast({ title: t('actions.disconnectSuccess') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('actions.errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  const projectMutation = useMutation({
    mutationFn: (projectId: string) =>
      updateChannel(channel.id!, {
        projectId: projectId === '__none__' ? undefined : projectId,
      }),
    onSuccess: async () => {
      await invalidate();
      toast({ title: t('actions.projectUpdated') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('actions.errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  return (
    <li
      className="flex flex-col gap-3 rounded-lg border border-border bg-card p-4"
      data-testid={`channel-card-${channel.id}`}
      data-channel-status={status}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          {icon ? <NetworkIcon network={icon} /> : null}
          <div className="min-w-0">
            <p className="truncate font-medium text-foreground">
              {channel.title ?? channel.username ?? channel.id}
            </p>
            {channel.username ? (
              <p className="truncate text-sm text-muted-foreground">{channel.username}</p>
            ) : null}
          </div>
        </div>
        <ChannelStatusBadge status={status} label={t(`status.${status}`)} />
      </div>

      {channel.statusReason ? (
        <p className="text-sm text-muted-foreground">{channel.statusReason}</p>
      ) : null}

      {canManage ? (
        <div className="flex flex-col gap-2">
          <label className="text-xs font-medium text-muted-foreground" htmlFor={`project-${channel.id}`}>
            {t('project.label')}
          </label>
          <Select
            value={channel.projectId ?? '__none__'}
            onValueChange={(value) => projectMutation.mutate(value)}
          >
            <SelectTrigger id={`project-${channel.id}`} aria-label={t('project.label')}>
              <SelectValue placeholder={t('project.placeholder')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__none__">{t('project.none')}</SelectItem>
              {projects.map((project) => (
                <SelectItem key={project.id} value={project.id!}>
                  {project.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      ) : null}

      {canManage ? (
        <div className="flex flex-wrap gap-2">
          {(status === 'reauth_required' || status === 'no_permissions') &&
          (channel.network === 'vk' ||
            channel.network === 'tg' ||
            channel.network === 'ok' ||
            channel.network === 'max') ? (
            <Button
              type="button"
              size="sm"
              onClick={() => onReconnect(channel.network as ConnectableNetwork)}
              data-testid={`reconnect-${channel.id}`}
            >
              {t('actions.reconnect')}
            </Button>
          ) : null}
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => checkMutation.mutate()}
            disabled={checkMutation.isPending}
            data-testid={`check-${channel.id}`}
          >
            {t('actions.check')}
          </Button>
          <Button
            type="button"
            size="sm"
            variant="destructive"
            onClick={() => disconnectMutation.mutate()}
            disabled={disconnectMutation.isPending}
            data-testid={`disconnect-${channel.id}`}
          >
            {t('actions.disconnect')}
          </Button>
        </div>
      ) : null}
    </li>
  );
}
