'use client';

import { useQuery } from '@tanstack/react-query';
import { useSyncExternalStore } from 'react';

import { listChannels } from '@/features/channels/api/channels-api';
import { listPosts } from '@/features/posts/api/posts-api';
import { getWorkspace } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';

import {
  computeOnboardingProgress,
  dismissOnboarding,
  isOnboardingDismissed,
  type OnboardingProgress,
} from '../model/onboarding';

function subscribeStorage(onStoreChange: () => void) {
  if (typeof window === 'undefined') return () => undefined;
  const handler = () => onStoreChange();
  window.addEventListener('storage', handler);
  return () => window.removeEventListener('storage', handler);
}

export function useOnboardingProgress(workspaceId: string): {
  progress: OnboardingProgress | null;
  isLoading: boolean;
  dismiss: () => void;
} {
  const dismissed = useSyncExternalStore(
    subscribeStorage,
    () => isOnboardingDismissed(workspaceId),
    () => false,
  );

  const workspaceQuery = useQuery({
    queryKey: queryKeys.workspaces.detail(workspaceId),
    queryFn: () => getWorkspace(workspaceId),
  });

  const channelsQuery = useQuery({
    queryKey: queryKeys.channels.list(workspaceId),
    queryFn: () => listChannels({ workspaceId, limit: 5 }),
  });

  const postsQuery = useQuery({
    queryKey: queryKeys.posts.list(workspaceId, { onboarding: true }),
    queryFn: () => listPosts({ workspaceId, limit: 1 }),
  });

  const isLoading =
    workspaceQuery.isLoading || channelsQuery.isLoading || postsQuery.isLoading;

  const progress =
    workspaceQuery.data || channelsQuery.data || postsQuery.data
      ? computeOnboardingProgress({
          workspaceId,
          hasWorkspace: Boolean(workspaceQuery.data?.id),
          hasChannel: (channelsQuery.data?.items?.length ?? 0) > 0,
          hasPost: (postsQuery.data?.items?.length ?? 0) > 0,
          dismissed,
        })
      : null;

  return {
    progress,
    isLoading,
    dismiss: () => {
      dismissOnboarding(workspaceId);
      // Force re-read via storage event for same-tab listeners.
      window.dispatchEvent(new Event('storage'));
    },
  };
}
