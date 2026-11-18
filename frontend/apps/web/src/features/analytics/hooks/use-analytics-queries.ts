'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { retryPublication } from '@/features/posts/api/posts-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';

import { getPostsAnalytics } from '../api/analytics-api';

export function usePostsAnalytics(workspaceId: string, year: number, enabled = true) {
  const canView = useCan('analytics.view', workspaceId);
  return useQuery({
    queryKey: queryKeys.analytics.posts(workspaceId, year),
    queryFn: () => getPostsAnalytics(workspaceId, year),
    enabled: enabled && canView && Number.isFinite(year),
  });
}

export function useRetryPublicationMutation(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (publicationId: string) => retryPublication(publicationId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.analytics.all(workspaceId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.posts.all(workspaceId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.publications.all(workspaceId) }),
      ]);
    },
  });
}
