'use client';

import type { Post } from '@posts/api-client';
import {
  Button,
  EmptyState,
  Input,
  PostStatusBadge,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Skeleton,
  toast,
} from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useVirtualizer } from '@tanstack/react-virtual';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';
import { Megaphone, Plus, ShieldOff } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useMemo, useRef, useState, type CSSProperties } from 'react';

import { listChannels } from '@/features/channels/api/channels-api';
import { listMembers } from '@/features/team/api/team-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';
import { usePublicationUpdates } from '@/shared/ws/use-publication-updates';

import { bulkPosts, listPosts } from '../api/posts-api';
import {
  clearSelection,
  selectAll,
  toggleSelection,
  type BulkAction,
} from '../model/bulk';
import {
  defaultPostsFilters,
  POST_STATUS_OPTIONS,
  postsFiltersToQuery,
  type PostsFiltersState,
  type PostsSort,
} from '../model/filters';
import { BulkActionsBar } from './bulk-actions-bar';

const NETWORK_OPTIONS = ['vk', 'tg', 'ok', 'max', 'rutube'] as const;

type PostsListPageProps = {
  workspaceId: string;
};

function filtersFromSearchParams(params: URLSearchParams): PostsFiltersState {
  const base = defaultPostsFilters();
  const status = params.get('status') ?? undefined;
  const network = params.get('network') ?? undefined;
  const q = params.get('q') ?? undefined;
  const channelId = params.get('channelId') ?? undefined;
  const projectId = params.get('projectId') ?? undefined;
  const contentKind = params.get('contentKind');
  return {
    ...base,
    ...(status ? { status } : {}),
    ...(network ? { network } : {}),
    ...(q ? { q } : {}),
    ...(channelId ? { channelId } : {}),
    ...(projectId ? { projectId } : {}),
    ...(contentKind === 'post' || contentKind === 'story' ? { contentKind } : {}),
  };
}

export function PostsListPage({ workspaceId }: PostsListPageProps) {
  const t = useTranslations('posts');
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const canView = useCan('posts.view', workspaceId);
  const canBulk = useCan('posts.bulk', workspaceId);
  const canDelete = useCan('posts.delete', workspaceId);

  usePublicationUpdates({ workspaceId, enabled: canView });

  const [filters, setFilters] = useState<PostsFiltersState>(() =>
    filtersFromSearchParams(searchParams),
  );
  const [selected, setSelected] = useState<Set<string>>(() => clearSelection());
  const [cursorStack, setCursorStack] = useState<(string | undefined)[]>([undefined]);
  const currentCursor = cursorStack[cursorStack.length - 1];

  const listQuery = useQuery({
    queryKey: queryKeys.posts.list(workspaceId, {
      ...postsFiltersToQuery(filters),
      cursor: currentCursor,
    }),
    queryFn: () =>
      listPosts({
        workspaceId,
        ...postsFiltersToQuery(filters),
        cursor: currentCursor,
        limit: 50,
      }),
    enabled: canView,
  });

  const channelsQuery = useQuery({
    queryKey: queryKeys.channels.list(workspaceId),
    queryFn: () => listChannels({ workspaceId, limit: 100 }),
    enabled: canView,
  });

  const membersQuery = useQuery({
    queryKey: queryKeys.workspaces.members(workspaceId),
    queryFn: () => listMembers(workspaceId),
    enabled: canView,
  });

  const posts = useMemo(() => listQuery.data?.items ?? [], [listQuery.data?.items]);
  const parentRef = useRef<HTMLDivElement>(null);
  const virtualizer = useVirtualizer({
    count: posts.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 64,
    overscan: 8,
  });

  const bulkMutation = useMutation({
    mutationFn: async ({
      action,
      scheduledAt,
    }: {
      action: BulkAction;
      scheduledAt?: string;
    }) => {
      const postIds = [...selected];
      const contractedAction =
        action === 'pause' ||
        action === 'resume' ||
        action === 'delete' ||
        action === 'reschedule' ||
        action === 'duplicate'
          ? action
          : 'pause';
      return bulkPosts({
        action: contractedAction,
        postIds,
        scheduledAt,
      });
    },
    onSuccess: async (result, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.posts.all(workspaceId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.calendar.all(workspaceId) }),
      ]);
      setSelected(clearSelection());
      toast({
        title: t('bulk.successTitle'),
        description: t('bulk.successBody', {
          affected: result.affected ?? 0,
          action: t(`bulk.${variables.action}`),
        }),
      });
    },
    onError: (error: Error) => {
      toast({
        title: t('bulk.errorTitle'),
        description: error.message,
        variant: 'destructive',
      });
    },
  });

  if (!canView) {
    return (
      <EmptyState
        icon={<ShieldOff className="h-6 w-6" aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
        data-testid="posts-forbidden"
      />
    );
  }

  if (listQuery.isLoading) {
    return (
      <div className="space-y-3" data-testid="posts-loading">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  const allSelected = posts.length > 0 && posts.every((p) => p.id && selected.has(p.id));

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-4" data-testid="posts-list-page">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t('title')}</h1>
          <p className="text-sm text-muted-foreground">{t('description')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="outline">
            <Link href={`/app/${workspaceId}/posts/batch`} data-testid="posts-batch">
              {t('batch')}
            </Link>
          </Button>
          <Button asChild>
            <Link href={`/app/${workspaceId}/posts/new`} data-testid="posts-create">
              <Plus aria-hidden />
              {t('create')}
            </Link>
          </Button>
        </div>
      </header>

      <div className="flex flex-wrap items-end gap-2" data-testid="posts-filters">
        <label className="flex min-w-[10rem] flex-1 flex-col gap-1 text-xs text-muted-foreground">
          {t('filters.search')}
          <Input
            value={filters.q ?? ''}
            onChange={(e) => {
              setCursorStack([undefined]);
              setFilters((f) => ({ ...f, q: e.target.value }));
            }}
            placeholder={t('filters.searchPlaceholder')}
            data-testid="posts-filter-q"
          />
        </label>

        <FilterSelect
          label={t('filters.status')}
          value={filters.status ?? 'all'}
          onChange={(value) => {
            setCursorStack([undefined]);
            setFilters((f) => ({ ...f, status: value === 'all' ? undefined : value }));
          }}
          options={[
            { value: 'all', label: t('filters.allStatuses') },
            ...POST_STATUS_OPTIONS.map((status) => ({
              value: status,
              label: t(`status.${status}`),
            })),
          ]}
          testId="posts-filter-status"
        />

        <FilterSelect
          label={t('filters.network')}
          value={filters.network ?? 'all'}
          onChange={(value) => {
            setCursorStack([undefined]);
            setFilters((f) => ({ ...f, network: value === 'all' ? undefined : value }));
          }}
          options={[
            { value: 'all', label: t('filters.allNetworks') },
            ...NETWORK_OPTIONS.map((network) => ({
              value: network,
              label: t(`networks.${network}`),
            })),
          ]}
          testId="posts-filter-network"
        />

        <FilterSelect
          label={t('filters.contentKind')}
          value={filters.contentKind ?? 'all'}
          onChange={(value) => {
            setCursorStack([undefined]);
            setFilters((f) => ({
              ...f,
              contentKind: value === 'all' ? undefined : (value as 'post' | 'story'),
            }));
          }}
          options={[
            { value: 'all', label: t('filters.allKinds') },
            { value: 'post', label: t('filters.posts') },
            { value: 'story', label: t('filters.stories') },
          ]}
          testId="posts-filter-content-kind"
        />

        <FilterSelect
          label={t('filters.channel')}
          value={filters.channelId ?? 'all'}
          onChange={(value) => {
            setCursorStack([undefined]);
            setFilters((f) => ({ ...f, channelId: value === 'all' ? undefined : value }));
          }}
          options={[
            { value: 'all', label: t('filters.allChannels') },
            ...(channelsQuery.data?.items ?? []).map((ch) => ({
              value: ch.id!,
              label: ch.title ?? ch.id!,
            })),
          ]}
          testId="posts-filter-channel"
        />

        <FilterSelect
          label={t('filters.author')}
          value={filters.authorId ?? 'all'}
          onChange={(value) => {
            setCursorStack([undefined]);
            setFilters((f) => ({ ...f, authorId: value === 'all' ? undefined : value }));
          }}
          options={[
            { value: 'all', label: t('filters.allAuthors') },
            ...(membersQuery.data?.items ?? []).map((m) => ({
              value: m.userId!,
              label: m.name ?? m.email ?? m.userId!,
            })),
          ]}
          testId="posts-filter-author"
        />

        <label className="flex flex-col gap-1 text-xs text-muted-foreground">
          {t('filters.from')}
          <Input
            type="date"
            value={filters.from?.slice(0, 10) ?? ''}
            onChange={(e) => {
              setCursorStack([undefined]);
              setFilters((f) => ({
                ...f,
                from: e.target.value ? new Date(e.target.value).toISOString() : undefined,
              }));
            }}
            data-testid="posts-filter-from"
          />
        </label>
        <label className="flex flex-col gap-1 text-xs text-muted-foreground">
          {t('filters.to')}
          <Input
            type="date"
            value={filters.to?.slice(0, 10) ?? ''}
            onChange={(e) => {
              setCursorStack([undefined]);
              setFilters((f) => ({
                ...f,
                to: e.target.value ? new Date(`${e.target.value}T23:59:59`).toISOString() : undefined,
              }));
            }}
            data-testid="posts-filter-to"
          />
        </label>

        <FilterSelect
          label={t('filters.sort')}
          value={filters.sort}
          onChange={(value) => {
            setCursorStack([undefined]);
            setFilters((f) => ({ ...f, sort: value as PostsSort }));
          }}
          options={[
            { value: 'scheduledAt_desc', label: t('sort.scheduledDesc') },
            { value: 'scheduledAt_asc', label: t('sort.scheduledAsc') },
            { value: 'title_asc', label: t('sort.titleAsc') },
            { value: 'updatedAt_desc', label: t('sort.updatedDesc') },
          ]}
          testId="posts-filter-sort"
        />
      </div>

      {posts.length === 0 ? (
        <EmptyState
          icon={<Megaphone className="h-6 w-6" aria-hidden />}
          title={t('emptyTitle')}
          description={t('emptyBody')}
        />
      ) : (
        <>
          <div className="flex items-center gap-2 text-sm">
            <label className="inline-flex items-center gap-2">
              <input
                type="checkbox"
                checked={allSelected}
                onChange={() => {
                  setSelected(
                    allSelected
                      ? clearSelection()
                      : selectAll(posts.map((p) => p.id!).filter(Boolean)),
                  );
                }}
                data-testid="posts-select-all"
              />
              {t('selectAll')}
            </label>
            <span className="text-muted-foreground">
              {t('count', { count: posts.length })}
            </span>
          </div>

          <div
            ref={parentRef}
            className="h-[min(70vh,36rem)] overflow-auto rounded-md border border-border"
            data-testid="posts-virtual-list"
          >
            <div
              style={{ height: virtualizer.getTotalSize(), position: 'relative', width: '100%' }}
            >
              {virtualizer.getVirtualItems().map((row) => {
                const post = posts[row.index]!;
                return (
                  <PostListRow
                    key={post.id}
                    post={post}
                    style={{
                      position: 'absolute',
                      top: 0,
                      left: 0,
                      width: '100%',
                      height: row.size,
                      transform: `translateY(${row.start}px)`,
                    }}
                    selected={Boolean(post.id && selected.has(post.id))}
                    onToggle={() => post.id && setSelected((s) => toggleSelection(s, post.id!))}
                    onOpen={() =>
                      post.id && router.push(`/app/${workspaceId}/posts/${post.id}`)
                    }
                    statusLabel={t(`status.${post.status ?? 'draft'}`)}
                    untitled={t('untitled')}
                  />
                );
              })}
            </div>
          </div>

          <div className="flex items-center justify-between gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={cursorStack.length <= 1}
              onClick={() => setCursorStack((s) => s.slice(0, -1))}
            >
              {t('pagination.prev')}
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={!listQuery.data?.hasMore}
              onClick={() => {
                if (listQuery.data?.nextCursor) {
                  setCursorStack((s) => [...s, listQuery.data!.nextCursor]);
                }
              }}
              data-testid="posts-next-page"
            >
              {t('pagination.next')}
            </Button>
          </div>
        </>
      )}

      <BulkActionsBar
        selectedCount={selected.size}
        canBulk={canBulk}
        canDelete={canDelete}
        busy={bulkMutation.isPending}
        onClear={() => setSelected(clearSelection())}
        onAction={(action, scheduledAt) => bulkMutation.mutate({ action, scheduledAt })}
      />
    </div>
  );
}

type FilterSelectProps = {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
  testId?: string;
};

function FilterSelect({ label, value, onChange, options, testId }: FilterSelectProps) {
  return (
    <label className="flex min-w-[9rem] flex-col gap-1 text-xs text-muted-foreground">
      {label}
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger data-testid={testId}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {options.map((opt) => (
            <SelectItem key={opt.value} value={opt.value}>
              {opt.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </label>
  );
}

type PostListRowProps = {
  post: Post;
  selected: boolean;
  onToggle: () => void;
  onOpen: () => void;
  statusLabel: string;
  untitled: string;
  style: CSSProperties;
};

function PostListRow({
  post,
  selected,
  onToggle,
  onOpen,
  statusLabel,
  untitled,
  style,
}: PostListRowProps) {
  return (
    <div
      style={style}
      className="flex items-center gap-3 border-b border-border px-3 hover:bg-muted/40"
      data-testid="posts-row"
      data-post-id={post.id}
    >
      <input
        type="checkbox"
        checked={selected}
        onChange={onToggle}
        aria-label={post.title ?? untitled}
        data-testid="posts-row-select"
      />
      <button
        type="button"
        className="flex min-w-0 flex-1 items-center gap-3 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        onClick={onOpen}
      >
        <div className="min-w-0 flex-1">
          <div className="truncate font-medium">{post.title || untitled}</div>
          <div className="truncate text-xs text-muted-foreground">
            {post.scheduledAt
              ? format(new Date(post.scheduledAt), 'd MMM yyyy, HH:mm', { locale: ru })
              : '—'}
          </div>
        </div>
        <PostStatusBadge status={post.status ?? 'draft'} label={statusLabel} />
      </button>
    </div>
  );
}
