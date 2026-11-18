'use client';

import type { Post } from '@posts/api-client';
import {
  Button,
  EmptyState,
  PostStatusBadge,
  Skeleton,
  toast,
} from '@posts/ui';
import { useQuery } from '@tanstack/react-query';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';
import { Layers3, Plus, ShieldOff } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useMemo, useState } from 'react';

import { listPosts } from '@/features/posts/api/posts-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';

type StoriesListPageProps = {
  workspaceId: string;
};

export function StoriesListPage({ workspaceId }: StoriesListPageProps) {
  const t = useTranslations('stories');
  const canView = useCan('posts.view', workspaceId);
  const canCreate = useCan('posts.create', workspaceId);
  const [status, setStatus] = useState<string | undefined>();

  const listQuery = useQuery({
    queryKey: queryKeys.posts.list(workspaceId, {
      contentKind: 'story',
      status,
      sort: 'scheduledAt_desc',
    }),
    queryFn: () =>
      listPosts({
        workspaceId,
        contentKind: 'story',
        status,
        sort: 'scheduledAt_desc',
        limit: 50,
      }),
    enabled: canView,
  });

  const stories = useMemo(() => listQuery.data?.items ?? [], [listQuery.data?.items]);

  if (!canView) {
    return (
      <EmptyState
        icon={<ShieldOff aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
      />
    );
  }

  return (
    <div className="flex flex-col gap-4" data-testid="stories-list-page">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t('title')}</h1>
          <p className="text-sm text-muted-foreground">{t('description')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="outline">
            <Link href={`/app/${workspaceId}/stories/batch`}>
              <Layers3 className="mr-2 h-4 w-4" aria-hidden />
              {t('actions.batch')}
            </Link>
          </Button>
          {canCreate ? (
            <Button asChild data-testid="stories-create">
              <Link href={`/app/${workspaceId}/stories/new`}>
                <Plus className="mr-2 h-4 w-4" aria-hidden />
                {t('create')}
              </Link>
            </Button>
          ) : null}
        </div>
      </header>

      <div className="flex flex-wrap gap-2" data-testid="stories-filters">
        <label className="flex items-center gap-2 text-sm">
          <span className="text-muted-foreground">{t('filters.status')}</span>
          <select
            className="h-9 rounded-md border border-input bg-background px-2 text-sm"
            value={status ?? 'all'}
            onChange={(event) =>
              setStatus(event.target.value === 'all' ? undefined : event.target.value)
            }
            aria-label={t('filters.status')}
          >
            <option value="all">{t('filters.allStatuses')}</option>
            <option value="draft">{t('status.draft')}</option>
            <option value="scheduled">{t('status.scheduled')}</option>
            <option value="published">{t('status.published')}</option>
            <option value="failed">{t('status.failed')}</option>
            <option value="paused">{t('status.paused')}</option>
          </select>
        </label>
      </div>

      {listQuery.isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
        </div>
      ) : listQuery.isError ? (
        <EmptyState
          title={t('errorTitle')}
          description={t('errorBody')}
          action={
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                void listQuery.refetch().catch((error) => {
                  toast({
                    variant: 'destructive',
                    title: t('errorTitle'),
                    description: error instanceof Error ? error.message : undefined,
                  });
                });
              }}
            >
              {t('retry')}
            </Button>
          }
        />
      ) : stories.length === 0 ? (
        <EmptyState
          title={t('emptyTitle')}
          description={t('emptyBody')}
          action={
            canCreate ? (
              <Button asChild>
                <Link href={`/app/${workspaceId}/stories/new`}>{t('create')}</Link>
              </Button>
            ) : undefined
          }
        />
      ) : (
        <ul className="divide-y divide-border rounded-lg border border-border bg-card">
          {stories.map((story) => (
            <StoryRow key={story.id} story={story} workspaceId={workspaceId} />
          ))}
        </ul>
      )}
    </div>
  );
}

function StoryRow({ story, workspaceId }: { story: Post; workspaceId: string }) {
  const t = useTranslations('stories');
  return (
    <li>
      <Link
        href={`/app/${workspaceId}/posts/${story.id}`}
        className="flex items-center justify-between gap-3 px-4 py-3 hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        data-testid={`story-row-${story.id}`}
        data-content-kind="story"
      >
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="rounded bg-violet-500/15 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-violet-700 dark:text-violet-300">
              {t('badge')}
            </span>
            <p className="truncate font-medium">{story.title || t('untitled')}</p>
          </div>
          <p className="text-xs text-muted-foreground">
            {story.scheduledAt
              ? format(new Date(story.scheduledAt), 'd MMM yyyy, HH:mm', { locale: ru })
              : t('noSchedule')}
            {story.story?.ttl ? ` · ${story.story.ttl}` : ''}
          </p>
        </div>
        {story.status ? (
          <PostStatusBadge
            status={story.status}
            label={t(`status.${story.status}` as 'status.draft')}
          />
        ) : null}
      </Link>
    </li>
  );
}
