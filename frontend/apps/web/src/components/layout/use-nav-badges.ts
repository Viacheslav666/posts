'use client';

import { useQuery } from '@tanstack/react-query';

import { listSuggestItems, listSuggestSettings } from '@/features/suggest/api/suggest-api';
import { listProjects } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';

/** Pending suggest items already exposed by the suggest API. Other badge slots stay unset. */
export function useNavBadges(workspaceId: string, enabled: boolean): { suggest?: number } {
  const projects = useQuery({
    queryKey: queryKeys.projects.list(workspaceId),
    queryFn: () => listProjects(workspaceId),
    enabled,
    staleTime: 60_000,
  });
  const projectId = projects.data?.items?.[0]?.id;
  const settings = useQuery({
    // Keep this off the suggest page's infinite-query key. Sharing it makes
    // useInfiniteQuery read a finite page and the Предложка screen never mounts.
    queryKey: queryKeys.suggest.all(workspaceId).concat(['nav-settings', projectId ?? '']),
    queryFn: () => listSuggestSettings({ workspaceId, projectId: projectId!, limit: 20 }),
    enabled: enabled && Boolean(projectId),
    staleTime: 60_000,
  });
  const ids = (settings.data?.items ?? [])
    .map((item) => item.id)
    .filter((id): id is string => Boolean(id));
  const pending = useQuery({
    queryKey: queryKeys.suggest.all(workspaceId).concat(['nav-pending', ids.join(',')]),
    queryFn: async () => {
      const pages = await Promise.all(
        ids.map((settingsId) =>
          listSuggestItems({ settingsId, status: 'pending_moderation', limit: 100 }),
        ),
      );
      return pages.reduce((sum, page) => sum + (page.items?.length ?? 0), 0);
    },
    enabled: enabled && ids.length > 0,
    staleTime: 30_000,
  });
  return { suggest: pending.data };
}
