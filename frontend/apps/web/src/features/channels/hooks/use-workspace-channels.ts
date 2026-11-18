'use client';

import type { Channel } from '@posts/api-client';
import { useQuery } from '@tanstack/react-query';

import { queryKeys } from '@/shared/api-client';
import { listProjects } from '@/features/workspace/api/workspace-api';

import { listChannels } from '../api/channels-api';

export type EditorChannelOption = {
  id: string;
  name: string;
  network: 'vk' | 'tg' | 'ok' | 'max' | 'rutube';
  projectName: string;
  /** Network-side id for {group_id} (VK community id, TG chat id, …). */
  externalId?: string;
  /** VK stories connect status (BE-16); OK does not require a separate connect. */
  storyConnectionStatus?: Channel['storyConnectionStatus'];
  status?: Channel['status'];
};

function toEditorOption(
  channel: Channel,
  projectNames: Map<string, string>,
): EditorChannelOption | null {
  if (!channel.id || !channel.network) {
    return null;
  }
  if (channel.status === 'disabled') {
    return null;
  }
  return {
    id: channel.id,
    name: channel.title ?? channel.username ?? channel.id,
    network: channel.network,
    projectName: channel.projectId
      ? (projectNames.get(channel.projectId) ?? '—')
      : '—',
    externalId: channel.externalId,
    storyConnectionStatus: channel.storyConnectionStatus,
    status: channel.status,
  };
}

/**
 * Channels for the post editor picker — sourced from GET /channels (+ projects for labels).
 */
export function useWorkspaceChannels(workspaceId: string) {
  const projectsQuery = useQuery({
    queryKey: queryKeys.projects.list(workspaceId),
    queryFn: () => listProjects(workspaceId),
    enabled: Boolean(workspaceId),
  });

  const channelsQuery = useQuery({
    queryKey: queryKeys.channels.list(workspaceId),
    queryFn: () => listChannels({ workspaceId, limit: 100 }),
    enabled: Boolean(workspaceId),
  });

  const projectNames = new Map(
    (projectsQuery.data?.items ?? []).map((project) => [project.id!, project.name ?? project.id!]),
  );

  const channels = (channelsQuery.data?.items ?? [])
    .map((channel) => toEditorOption(channel, projectNames))
    .filter((item): item is EditorChannelOption => item !== null);

  return {
    channels,
    isLoading: channelsQuery.isLoading || projectsQuery.isLoading,
    isError: channelsQuery.isError || projectsQuery.isError,
    error: channelsQuery.error ?? projectsQuery.error,
    refetch: async () => {
      await Promise.all([channelsQuery.refetch(), projectsQuery.refetch()]);
    },
  };
}
