'use client';

import {
  Button,
  EmptyState,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Skeleton,
  Tabs,
  TabsList,
  TabsTrigger,
  toast,
} from '@posts/ui';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CalendarPlus, ChevronLeft, ChevronRight, ShieldOff } from 'lucide-react';
import dynamic from 'next/dynamic';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useEffect, useMemo, useState } from 'react';

import { getPost } from '@/features/posts/api/posts-api';
import { POST_STATUS_OPTIONS } from '@/features/posts/model/filters';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';

import { moveCalendarItem } from '../api/calendar-api';
import { useCalendarItems } from '../hooks/use-calendar-items';
import {
  formatRangeLabel,
  rangeForView,
  shiftAnchor,
  type CalendarViewMode,
} from '../model/date-range';
import {
  resolveMoveChoice,
  type MoveConfirmChoice,
  type PendingMove,
} from '../model/dnd';
import {
  applyClientSearch,
  emptyCalendarFilters,
  type CalendarFiltersState,
} from '../model/filters';
import { MoveConfirmDialog } from './move-confirm-dialog';

const CalendarBoard = dynamic(
  () => import('./calendar-board').then((mod) => ({ default: mod.CalendarBoard })),
  {
    ssr: false,
    loading: () => (
      <div className="space-y-2" data-testid="calendar-board-loading">
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-96 w-full" />
      </div>
    ),
  },
);

const NETWORK_OPTIONS = ['vk', 'tg', 'ok', 'max', 'rutube'] as const;

type CalendarPageProps = {
  workspaceId: string;
};

function usePreferredDefaultMode(): CalendarViewMode {
  const [mode, setMode] = useState<CalendarViewMode>('month');

  useEffect(() => {
    const media = window.matchMedia('(max-width: 767px)');
    const sync = () => setMode(media.matches ? 'day' : 'month');
    sync();
    media.addEventListener('change', sync);
    return () => media.removeEventListener('change', sync);
  }, []);

  return mode;
}

export function CalendarPage({ workspaceId }: CalendarPageProps) {
  const t = useTranslations('calendar');
  const router = useRouter();
  const queryClient = useQueryClient();
  const canView = useCan('calendar.view', workspaceId);
  const canMove = useCan('calendar.move', workspaceId);

  const preferredMode = usePreferredDefaultMode();
  const [mode, setMode] = useState<CalendarViewMode | null>(null);
  const viewMode = mode ?? preferredMode;

  const [anchor, setAnchor] = useState(() => new Date());
  const [filters, setFilters] = useState<CalendarFiltersState>(emptyCalendarFilters);
  const [pending, setPending] = useState<PendingMove | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const range = useMemo(() => rangeForView(anchor, viewMode), [anchor, viewMode]);

  const itemsQuery = useCalendarItems({
    workspaceId,
    from: range.from,
    to: range.to,
    projectId: filters.projectId,
    channelId: filters.channelId,
    authorId: filters.authorId,
    network: filters.network,
    status: filters.status,
    contentKind: filters.contentKind,
    enabled: canView,
  });

  const items = useMemo(
    () => applyClientSearch(itemsQuery.data ?? [], filters.q),
    [itemsQuery.data, filters.q],
  );

  const moveMutation = useMutation({
    mutationFn: async (choice: MoveConfirmChoice) => {
      if (!pending) return null;
      const resolved = resolveMoveChoice(pending, choice);
      if (!resolved) return null;
      const post = await getPost(pending.postId);
      return moveCalendarItem(pending.postId, {
        scheduledAt: resolved.scheduledAt,
        version: post.version ?? pending.version,
      });
    },
    onSuccess: async (result) => {
      if (!result) {
        setPending(null);
        return;
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.calendar.all(workspaceId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.posts.all(workspaceId) }),
      ]);
      toast({ title: t('move.successTitle') });
      setPending(null);
    },
    onError: (error: Error) => {
      toast({
        title: t('move.errorTitle'),
        description: error.message,
        variant: 'destructive',
      });
    },
  });

  const handleMoveRequest = async (next: PendingMove) => {
    try {
      const post = await getPost(next.postId);
      setPending({
        ...next,
        version: post.version ?? 0,
        recurrence: Boolean(post.recurrence),
        title: post.title ?? next.title,
      });
    } catch {
      setPending(next);
    }
  };

  const handleChoice = (choice: MoveConfirmChoice) => {
    if (choice === 'cancel') {
      setPending(null);
      return;
    }
    moveMutation.mutate(choice);
  };

  if (!canView) {
    return (
      <EmptyState
        icon={<ShieldOff className="h-6 w-6" aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
        data-testid="calendar-forbidden"
      />
    );
  }

  const createHref = `/app/${workspaceId}/posts/new?scheduledAt=${encodeURIComponent(range.anchor.toISOString())}`;

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-4" data-testid="calendar-page">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t('title')}</h1>
          <p className="text-sm text-muted-foreground">{t('description')}</p>
        </div>
        <Button asChild>
          <Link href={createHref} data-testid="calendar-create-post">
            <CalendarPlus aria-hidden />
            {t('createPost')}
          </Link>
        </Button>
      </header>

      <div className="flex flex-wrap items-center gap-2">
        <Tabs
          value={viewMode}
          onValueChange={(value: string) => setMode(value as CalendarViewMode)}
        >
          <TabsList>
            <TabsTrigger value="month">{t('views.month')}</TabsTrigger>
            <TabsTrigger value="week">{t('views.week')}</TabsTrigger>
            <TabsTrigger value="day">{t('views.day')}</TabsTrigger>
          </TabsList>
        </Tabs>

        <div className="flex items-center gap-1">
          <Button
            type="button"
            variant="outline"
            size="icon"
            aria-label={t('nav.prev')}
            onClick={() => setAnchor((current) => shiftAnchor(current, viewMode, -1))}
          >
            <ChevronLeft aria-hidden />
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setAnchor(new Date())}
          >
            {t('nav.today')}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="icon"
            aria-label={t('nav.next')}
            onClick={() => setAnchor((current) => shiftAnchor(current, viewMode, 1))}
          >
            <ChevronRight aria-hidden />
          </Button>
        </div>

        <p className="min-w-[10rem] text-sm font-medium capitalize">
          {formatRangeLabel(anchor, viewMode)}
        </p>

        <div className="ml-auto flex flex-wrap items-center gap-2">
          <Select
            value={filters.status ?? 'all'}
            onValueChange={(value: string) =>
              setFilters((current) => ({
                ...current,
                status: value === 'all' ? undefined : value,
              }))
            }
          >
            <SelectTrigger className="w-[160px]" aria-label={t('filters.status')}>
              <SelectValue placeholder={t('filters.status')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t('filters.allStatuses')}</SelectItem>
              {POST_STATUS_OPTIONS.map((status) => (
                <SelectItem key={status} value={status}>
                  {t(`status.${status}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select
            value={filters.network ?? 'all'}
            onValueChange={(value: string) =>
              setFilters((current) => ({
                ...current,
                network: value === 'all' ? undefined : value,
              }))
            }
          >
            <SelectTrigger className="w-[140px]" aria-label={t('filters.network')}>
              <SelectValue placeholder={t('filters.network')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t('filters.allNetworks')}</SelectItem>
              {NETWORK_OPTIONS.map((network) => (
                <SelectItem key={network} value={network}>
                  {t(`networks.${network}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select
            value={filters.contentKind ?? 'all'}
            onValueChange={(value: string) =>
              setFilters((current) => ({
                ...current,
                contentKind:
                  value === 'all' ? undefined : (value as 'post' | 'story'),
              }))
            }
          >
            <SelectTrigger
              className="w-[150px]"
              aria-label={t('filters.contentKind')}
              data-testid="calendar-filter-content-kind"
            >
              <SelectValue placeholder={t('filters.contentKind')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t('filters.allKinds')}</SelectItem>
              <SelectItem value="post">{t('filters.posts')}</SelectItem>
              <SelectItem value="story">{t('filters.stories')}</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {itemsQuery.isLoading ? (
        <div className="space-y-2" data-testid="calendar-loading">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-96 w-full" />
        </div>
      ) : itemsQuery.isError ? (
        <EmptyState
          title={t('errorTitle')}
          description={t('errorBody')}
          action={
            <Button type="button" variant="outline" onClick={() => itemsQuery.refetch()}>
              {t('retry')}
            </Button>
          }
        />
      ) : (
        <CalendarBoard
          items={items}
          mode={viewMode}
          anchor={anchor}
          canMove={canMove}
          onMoveRequest={handleMoveRequest}
          onEventClick={(id) => {
            setSelectedId(id);
            router.push(`/app/${workspaceId}/posts/${id}`);
          }}
          selectedId={selectedId}
        />
      )}

      <MoveConfirmDialog
        pending={pending}
        open={Boolean(pending)}
        busy={moveMutation.isPending}
        onChoice={handleChoice}
      />
    </div>
  );
}
