'use client';

import { useQuery } from '@tanstack/react-query';

import { listChannels } from '@/features/channels/api/channels-api';
import { listIngestRules } from '@/features/grabber/api/grabber-api';
import { listPosts } from '@/features/posts/api/posts-api';
import { listMembers } from '@/features/team/api/team-api';
import { getWorkspace, listProjects } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';

import { listBillingPlans } from '../api/billing-api';
import { LIMIT_KEYS, readPlanLimit, type UsageMetric } from '../model/limits';

function startOfMonthIso(): string {
  const now = new Date();
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1)).toISOString();
}

export function useBillingPlans(enabled = true) {
  return useQuery({
    queryKey: queryKeys.billing.plans(),
    queryFn: listBillingPlans,
    enabled,
    staleTime: 60_000,
  });
}

export function useBillingUsage(workspaceId: string, enabled = true) {
  const canView = useCan('billing.view', workspaceId);
  const plansQuery = useBillingPlans(enabled && canView);
  const workspaceQuery = useQuery({
    queryKey: queryKeys.workspaces.detail(workspaceId),
    queryFn: () => getWorkspace(workspaceId),
    enabled: enabled && canView,
  });

  const channelsQuery = useQuery({
    queryKey: queryKeys.channels.list(workspaceId, 'billing-usage'),
    queryFn: () => listChannels({ workspaceId, limit: 100 }),
    enabled: enabled && canView,
  });
  const membersQuery = useQuery({
    queryKey: queryKeys.workspaces.members(workspaceId),
    queryFn: () => listMembers(workspaceId),
    enabled: enabled && canView,
  });
  const projectsQuery = useQuery({
    queryKey: queryKeys.projects.list(workspaceId),
    queryFn: () => listProjects(workspaceId),
    enabled: enabled && canView,
  });
  const projectId = projectsQuery.data?.items?.[0]?.id;

  const rulesQuery = useQuery({
    queryKey: queryKeys.ingest.rules(workspaceId, projectId ?? '', { billing: true }),
    queryFn: () => listIngestRules({ workspaceId, projectId: projectId! }),
    enabled: enabled && canView && Boolean(projectId),
  });

  const postsMonthQuery = useQuery({
    queryKey: queryKeys.posts.list(workspaceId, { billing: 'posts_month' }),
    queryFn: () =>
      listPosts({
        workspaceId,
        contentKind: 'post',
        from: startOfMonthIso(),
        limit: 100,
      }),
    enabled: enabled && canView,
  });

  const storiesMonthQuery = useQuery({
    queryKey: queryKeys.posts.list(workspaceId, { billing: 'stories_month' }),
    queryFn: () =>
      listPosts({
        workspaceId,
        contentKind: 'story',
        from: startOfMonthIso(),
        limit: 100,
      }),
    enabled: enabled && canView,
  });

  const planCode = workspaceQuery.data?.planCode ?? 'free';
  const plan = plansQuery.data?.find((item) => item.code === planCode);

  const metrics: UsageMetric[] = LIMIT_KEYS.map((key) => {
    const limit = readPlanLimit(plan, key);
    let used = 0;
    switch (key) {
      case 'channels':
        used = channelsQuery.data?.items?.length ?? 0;
        break;
      case 'seats':
        used = membersQuery.data?.items?.length ?? 0;
        break;
      case 'posts_month':
        used = postsMonthQuery.data?.items?.length ?? 0;
        break;
      case 'grabber_rules':
        used = rulesQuery.data?.items?.length ?? 0;
        break;
      case 'stories_month':
        used = storiesMonthQuery.data?.items?.length ?? 0;
        break;
      case 'ai_credits':
        used = 0;
        break;
      default:
        used = 0;
    }
    return { key, used, limit };
  });

  const isLoading =
    plansQuery.isLoading ||
    workspaceQuery.isLoading ||
    channelsQuery.isLoading ||
    membersQuery.isLoading;

  return {
    planCode,
    planName: plan?.name,
    plan,
    metrics,
    isLoading,
    isError: plansQuery.isError || workspaceQuery.isError,
  };
}
