'use client';

import type { Channel, Post } from '@posts/api-client';
import {
  Button,
  ChannelStatusBadge,
  EmptyState,
  PostStatusBadge,
  Skeleton,
} from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle,
  CalendarClock,
  Inbox,
  Plus,
  Radio,
  Sparkles,
} from 'lucide-react';
import Link from 'next/link';
import { useMemo } from 'react';
import { useTranslations } from 'next-intl';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';

import { listChannels } from '@/features/channels/api/channels-api';
import { listIngestItems, listIngestRules } from '@/features/grabber/api/grabber-api';
import { OnboardingChecklist } from '@/features/onboarding/components/onboarding-checklist';
import { listPosts, retryPublication, listPostPublications } from '@/features/posts/api/posts-api';
import { listProjects } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';
import { getErrorMessage } from '@/shared/lib/errors';
import { toast } from '@posts/ui';

type DashboardPageProps = {
  workspaceId: string;
};

function upcomingWindow() {
  const from = new Date().toISOString();
  const to = new Date(Date.now() + 7 * 24 * 60 * 60_000).toISOString();
  return { from, to };
}

function failedWindow() {
  const from = new Date(Date.now() - 24 * 60 * 60_000).toISOString();
  const to = new Date().toISOString();
  return { from, to };
}

export function DashboardPage({ workspaceId }: DashboardPageProps) {
  const t = useTranslations('dashboard');
  const queryClient = useQueryClient();
  const canPosts = useCan('posts.view', workspaceId);
  const canChannels = useCan('channels.view', workspaceId);
  const canAutomations = useCan('automations.view', workspaceId);
  const canRetry = useCan('posts.retry_publish', workspaceId);
  const canCreate = useCan('posts.create', workspaceId);
  const canManageChannels = useCan('channels.manage', workspaceId);

  const upcoming = upcomingWindow();
  const failed = failedWindow();

  const upcomingQuery = useQuery({
    queryKey: queryKeys.posts.list(workspaceId, { dashboard: 'upcoming', ...upcoming }),
    queryFn: () =>
      listPosts({
        workspaceId,
        status: 'scheduled',
        from: upcoming.from,
        to: upcoming.to,
        sort: 'scheduledAt_asc',
        limit: 5,
      }),
    enabled: canPosts,
  });

  const failedQuery = useQuery({
    queryKey: queryKeys.posts.list(workspaceId, { dashboard: 'failed', ...failed }),
    queryFn: () =>
      listPosts({
        workspaceId,
        status: 'failed',
        from: failed.from,
        to: failed.to,
        sort: 'updatedAt_desc',
        limit: 5,
      }),
    enabled: canPosts,
  });

  const storiesQuery = useQuery({
    queryKey: queryKeys.posts.list(workspaceId, { dashboard: 'stories', contentKind: 'story' }),
    queryFn: () =>
      listPosts({
        workspaceId,
        contentKind: 'story',
        status: 'scheduled',
        sort: 'scheduledAt_asc',
        limit: 5,
      }),
    enabled: canPosts,
  });

  const channelsQuery = useQuery({
    queryKey: queryKeys.channels.list(workspaceId, 'dashboard'),
    queryFn: () => listChannels({ workspaceId, limit: 50 }),
    enabled: canChannels,
  });

  const projectsQuery = useQuery({
    queryKey: queryKeys.projects.list(workspaceId),
    queryFn: () => listProjects(workspaceId),
    enabled: canAutomations,
  });
  const projectId = projectsQuery.data?.items?.[0]?.id;

  const rulesQuery = useQuery({
    queryKey: queryKeys.ingest.rules(workspaceId, projectId ?? '', { dashboard: true }),
    queryFn: () => listIngestRules({ workspaceId, projectId: projectId! }),
    enabled: canAutomations && Boolean(projectId),
  });
  const firstRuleId = rulesQuery.data?.items?.[0]?.id;

  const queueQuery = useQuery({
    queryKey: queryKeys.ingest.items(workspaceId, firstRuleId ?? '', {
      status: 'pending_moderation',
      dashboard: true,
    }),
    queryFn: () =>
      listIngestItems({
        ruleId: firstRuleId!,
        status: 'pending_moderation',
        limit: 5,
      }),
    enabled: canAutomations && Boolean(firstRuleId),
  });

  const attentionChannels = useMemo(
    () =>
      (channelsQuery.data?.items ?? []).filter(
        (ch) => ch.status === 'reauth_required' || ch.status === 'no_permissions',
      ),
    [channelsQuery.data?.items],
  );

  const retryMutation = useMutation({
    mutationFn: async (post: Post) => {
      const pubs = await listPostPublications(post.id!);
      const failedPub = pubs.items?.find(
        (p) => p.state === 'failed_retryable' || p.state === 'failed_permanent',
      );
      if (!failedPub?.id) {
        throw new Error('No failed publication');
      }
      return retryPublication(failedPub.id);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.posts.all(workspaceId) });
      toast({ title: t('failed.retrySuccess') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('failed.retryError'),
        description: getErrorMessage(error, t('failed.retryError')),
      });
    },
  });

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-8" data-testid="dashboard-page">
      <header className="space-y-2">
        <h1 className="font-display text-3xl font-bold tracking-tight text-foreground">
          {t('title')}
        </h1>
        <p className="max-w-2xl text-muted-foreground">{t('description')}</p>
      </header>

      <OnboardingChecklist workspaceId={workspaceId} />

      <section aria-label={t('quickActions.title')} data-testid="dashboard-quick-actions">
        <h2 className="mb-3 text-lg font-semibold">{t('quickActions.title')}</h2>
        <div className="flex flex-wrap gap-2">
          {canCreate ? (
            <Button asChild size="sm">
              <Link href={`/app/${workspaceId}/posts/new`}>
                <Plus aria-hidden />
                {t('quickActions.createPost')}
              </Link>
            </Button>
          ) : null}
          {canManageChannels ? (
            <Button asChild size="sm" variant="secondary">
              <Link href={`/app/${workspaceId}/channels/connect`}>
                <Radio aria-hidden />
                {t('quickActions.connectChannel')}
              </Link>
            </Button>
          ) : null}
          <Button asChild size="sm" variant="outline">
            <Link href={`/app/${workspaceId}/calendar`}>
              <CalendarClock aria-hidden />
              {t('quickActions.calendar')}
            </Link>
          </Button>
          {canAutomations ? (
            <Button asChild size="sm" variant="outline">
              <Link href={`/app/${workspaceId}/automations/grabber`}>
                <Inbox aria-hidden />
                {t('quickActions.grabber')}
              </Link>
            </Button>
          ) : null}
          {canCreate ? (
            <Button asChild size="sm" variant="outline">
              <Link href={`/app/${workspaceId}/stories/new`}>
                <Sparkles aria-hidden />
                {t('quickActions.story')}
              </Link>
            </Button>
          ) : null}
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <DashboardListCard
          title={t('upcoming.title')}
          testId="dashboard-upcoming"
          loading={upcomingQuery.isLoading}
          error={upcomingQuery.isError}
          empty={!canPosts || (upcomingQuery.data?.items?.length ?? 0) === 0}
          emptyTitle={t('upcoming.emptyTitle')}
          emptyBody={t('upcoming.emptyBody')}
          onRetry={() => void upcomingQuery.refetch()}
        >
          {(upcomingQuery.data?.items ?? []).map((post) => (
            <PostRow key={post.id} post={post} workspaceId={workspaceId} />
          ))}
        </DashboardListCard>

        <DashboardListCard
          title={t('failed.title')}
          testId="dashboard-failed"
          loading={failedQuery.isLoading}
          error={failedQuery.isError}
          empty={!canPosts || (failedQuery.data?.items?.length ?? 0) === 0}
          emptyTitle={t('failed.emptyTitle')}
          emptyBody={t('failed.emptyBody')}
          onRetry={() => void failedQuery.refetch()}
        >
          {(failedQuery.data?.items ?? []).map((post) => (
            <li
              key={post.id}
              className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 py-2 last:border-0"
            >
              <PostRow post={post} workspaceId={workspaceId} asFragment />
              {canRetry && post.id ? (
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  disabled={retryMutation.isPending}
                  onClick={() => retryMutation.mutate(post)}
                  data-testid="dashboard-retry"
                >
                  {t('failed.retry')}
                </Button>
              ) : null}
            </li>
          ))}
        </DashboardListCard>

        <DashboardListCard
          title={t('channels.title')}
          testId="dashboard-channels"
          loading={channelsQuery.isLoading}
          error={channelsQuery.isError}
          empty={!canChannels || attentionChannels.length === 0}
          emptyTitle={t('channels.emptyTitle')}
          emptyBody={t('channels.emptyBody')}
          onRetry={() => void channelsQuery.refetch()}
        >
          {attentionChannels.map((channel) => (
            <ChannelRow key={channel.id} channel={channel} workspaceId={workspaceId} />
          ))}
        </DashboardListCard>

        <DashboardListCard
          title={t('grabber.title')}
          testId="dashboard-grabber"
          loading={queueQuery.isLoading || rulesQuery.isLoading}
          error={queueQuery.isError}
          empty={
            !canAutomations ||
            !firstRuleId ||
            (queueQuery.data?.items?.length ?? 0) === 0
          }
          emptyTitle={t('grabber.emptyTitle')}
          emptyBody={t('grabber.emptyBody')}
          onRetry={() => void queueQuery.refetch()}
          footer={
            canAutomations ? (
              <Button asChild variant="ghost" size="sm">
                <Link href={`/app/${workspaceId}/automations/grabber`}>{t('grabber.open')}</Link>
              </Button>
            ) : null
          }
        >
          {(queueQuery.data?.items ?? []).map((item) => (
            <li key={item.id} className="border-b border-border/60 py-2 text-sm last:border-0">
              <p className="font-medium text-foreground">
                {item.contentText?.slice(0, 80) || item.sourceName || item.sourcePermalink || item.id}
              </p>
              <p className="text-xs text-muted-foreground">{t('grabber.pending')}</p>
            </li>
          ))}
        </DashboardListCard>

        <DashboardListCard
          title={t('stories.title')}
          testId="dashboard-stories"
          loading={storiesQuery.isLoading}
          error={storiesQuery.isError}
          empty={!canPosts || (storiesQuery.data?.items?.length ?? 0) === 0}
          emptyTitle={t('stories.emptyTitle')}
          emptyBody={t('stories.emptyBody')}
          onRetry={() => void storiesQuery.refetch()}
        >
          {(storiesQuery.data?.items ?? []).map((post) => (
            <PostRow key={post.id} post={post} workspaceId={workspaceId} />
          ))}
        </DashboardListCard>
      </div>
    </div>
  );
}

function DashboardListCard({
  title,
  testId,
  loading,
  error,
  empty,
  emptyTitle,
  emptyBody,
  onRetry,
  children,
  footer,
}: {
  title: string;
  testId: string;
  loading: boolean;
  error: boolean;
  empty: boolean;
  emptyTitle: string;
  emptyBody: string;
  onRetry: () => void;
  children: React.ReactNode;
  footer?: React.ReactNode;
}) {
  const t = useTranslations('dashboard');
  return (
    <section
      className="rounded-md border border-border p-4"
      data-testid={testId}
      aria-label={title}
    >
      <div className="mb-3 flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">{title}</h2>
        {footer}
      </div>
      {loading ? (
        <div className="space-y-2">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      ) : error ? (
        <EmptyState
          icon={<AlertTriangle className="h-5 w-5" aria-hidden />}
          title={t('errorTitle')}
          description={t('errorBody')}
          action={
            <Button type="button" size="sm" onClick={onRetry}>
              {t('retry')}
            </Button>
          }
        />
      ) : empty ? (
        <p className="text-sm text-muted-foreground">
          <span className="font-medium text-foreground">{emptyTitle}. </span>
          {emptyBody}
        </p>
      ) : (
        <ul className="space-y-0">{children}</ul>
      )}
    </section>
  );
}

function PostRow({
  post,
  workspaceId,
  asFragment = false,
}: {
  post: Post;
  workspaceId: string;
  asFragment?: boolean;
}) {
  const content = (
    <>
      <Link
        href={`/app/${workspaceId}/posts/${post.id}`}
        className="min-w-0 flex-1 truncate text-sm font-medium text-foreground hover:underline"
      >
        {post.title || post.id}
      </Link>
      <div className="flex items-center gap-2">
        {post.status ? (
          <PostStatusBadge status={post.status} label={post.status} />
        ) : null}
        {post.scheduledAt ? (
          <time className="text-xs text-muted-foreground" dateTime={post.scheduledAt}>
            {format(new Date(post.scheduledAt), 'd MMM HH:mm', { locale: ru })}
          </time>
        ) : null}
      </div>
    </>
  );
  if (asFragment) {
    return <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2">{content}</div>;
  }
  return (
    <li className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 py-2 last:border-0">
      {content}
    </li>
  );
}

function ChannelRow({ channel, workspaceId }: { channel: Channel; workspaceId: string }) {
  const t = useTranslations('channels');
  const status = channel.status;
  return (
    <li className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 py-2 last:border-0">
      <Link
        href={`/app/${workspaceId}/channels`}
        className="text-sm font-medium text-foreground hover:underline"
      >
        {channel.title || channel.externalId || channel.id}
      </Link>
      {status === 'active' ||
      status === 'reauth_required' ||
      status === 'no_permissions' ||
      status === 'disabled' ? (
        <ChannelStatusBadge status={status} label={t(`status.${status}`)} />
      ) : null}
    </li>
  );
}
