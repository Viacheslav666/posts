'use client';

import type { SuggestBulkJob, SuggestItem, SuggestSettings } from '@posts/api-client';
import {
  Button,
  EmptyState,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Skeleton,
  toast,
} from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Inbox, RefreshCw } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import {
  approveSuggestItem,
  bulkSuggestItems,
  getSuggestBulkJob,
  listSuggestItems,
  rejectSuggestItem,
  syncSuggestLast100,
} from '../api/suggest-api';
import { withConfigDefaults } from '../model/config';
import { QUEUE_FILTERS, type ItemStatus } from '../model/types';
import { ApproveEditDialog } from './approve-edit-dialog';
import { ItemCard } from './item-card';

type ModerationQueueProps = {
  workspaceId: string;
  settings: SuggestSettings;
  canModerate: boolean;
  canManage: boolean;
};

export function ModerationQueue({
  workspaceId,
  settings,
  canModerate,
  canManage,
}: ModerationQueueProps) {
  const t = useTranslations('automations.suggest.queue');
  const queryClient = useQueryClient();
  const settingsId = settings.id!;
  const [statusFilter, setStatusFilter] = useState<ItemStatus | 'all'>('pending_moderation');
  const [authorVkId, setAuthorVkId] = useState('');
  const [selected, setSelected] = useState<string[]>([]);
  const [editItem, setEditItem] = useState<SuggestItem | null>(null);
  const [bulkJobId, setBulkJobId] = useState<string | null>(null);

  const publishEditMode = withConfigDefaults(settings.config).publish_edit_mode;

  const itemsQuery = useQuery({
    queryKey: queryKeys.suggest.items(workspaceId, settingsId, {
      status: statusFilter === 'all' ? undefined : statusFilter,
      authorVkId: authorVkId || undefined,
    }),
    queryFn: () =>
      listSuggestItems({
        settingsId,
        status: statusFilter === 'all' ? undefined : statusFilter,
        authorVkId: authorVkId || undefined,
      }),
  });

  const items = useMemo(() => itemsQuery.data?.items ?? [], [itemsQuery.data?.items]);

  const bulkJobQuery = useQuery({
    queryKey: queryKeys.suggest.bulkJob(workspaceId, bulkJobId ?? ''),
    queryFn: () => getSuggestBulkJob(bulkJobId!),
    enabled: Boolean(bulkJobId),
    refetchInterval: (query) => {
      const job = query.state.data as SuggestBulkJob | undefined;
      if (!job) return 500;
      if ((job.progressPercent ?? 0) >= 100 || job.status === 'completed' || job.finishedAt) {
        return false;
      }
      return 500;
    },
  });

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: queryKeys.suggest.all(workspaceId) });
  };

  const optimisticUpdate = (itemId: string, next: Partial<SuggestItem>) => {
    const key = queryKeys.suggest.items(workspaceId, settingsId, {
      status: statusFilter === 'all' ? undefined : statusFilter,
      authorVkId: authorVkId || undefined,
    });
    const previous = queryClient.getQueryData(key);
    queryClient.setQueryData(key, (old: { items?: SuggestItem[] } | undefined) => {
      if (!old?.items) return old;
      return {
        ...old,
        items: old.items.map((item) => (item.id === itemId ? { ...item, ...next } : item)),
      };
    });
    return previous;
  };

  const moderateMutation = useMutation({
    mutationFn: async (input: {
      action: 'approve' | 'reject' | 'edit';
      itemId: string;
      message?: string;
      deleteRemote?: boolean;
    }) => {
      if (input.action === 'approve') return approveSuggestItem(input.itemId);
      if (input.action === 'reject') {
        return rejectSuggestItem(input.itemId, { deleteRemote: input.deleteRemote });
      }
      return approveSuggestItem(input.itemId, { message: input.message });
    },
    onMutate: async (variables) => {
      const nextStatus =
        variables.action === 'reject'
          ? variables.deleteRemote === false
            ? 'rejected'
            : 'deleted_remote'
          : 'published';
      const previous = optimisticUpdate(variables.itemId, { status: nextStatus });
      return { previous };
    },
    onError: (error, _variables, context) => {
      if (context?.previous) {
        queryClient.setQueryData(
          queryKeys.suggest.items(workspaceId, settingsId, {
            status: statusFilter === 'all' ? undefined : statusFilter,
            authorVkId: authorVkId || undefined,
          }),
          context.previous,
        );
      }
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
    onSuccess: async (_data, variables) => {
      await invalidate();
      setSelected((prev) => prev.filter((id) => id !== variables.itemId));
      setEditItem(null);
      toast({
        title:
          variables.action === 'reject'
            ? t('rejectSuccess')
            : variables.action === 'edit'
              ? t('editSuccess')
              : t('approveSuccess'),
      });
    },
  });

  const bulkMutation = useMutation({
    mutationFn: async (action: 'approve_ids' | 'approve_all' | 'reject_all') => {
      const confirmed =
        action === 'approve_ids'
          ? window.confirm(t('confirmBulkSelected', { count: selected.length }))
          : action === 'approve_all'
            ? window.confirm(t('confirmApproveAll', { count: items.filter((i) => i.status === 'pending_moderation').length || items.length }))
            : window.confirm(t('confirmRejectAll'));
      if (!confirmed) throw new Error('cancelled');
      return bulkSuggestItems(settingsId, {
        action,
        ids: action === 'approve_ids' ? selected : undefined,
        deleteRemote: action === 'reject_all' ? true : undefined,
      });
    },
    onSuccess: async (job) => {
      setBulkJobId(job.id ?? null);
      setSelected([]);
      await invalidate();
      toast({
        title: t('bulkStarted'),
        description: t('bulkProgress', {
          percent: job.progressPercent ?? 100,
          succeeded: job.succeeded ?? 0,
          failed: job.failed ?? 0,
        }),
      });
    },
    onError: (error) => {
      if (error instanceof Error && error.message === 'cancelled') return;
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  const syncMutation = useMutation({
    mutationFn: () => syncSuggestLast100(settingsId),
    onSuccess: async (result) => {
      await invalidate();
      toast({
        title: t('syncSuccess'),
        description: t('syncResult', {
          imported: result.imported ?? 0,
          removed: result.removedExternally ?? 0,
        }),
      });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  const job = bulkJobQuery.data;

  return (
    <section className="space-y-4" data-testid="suggest-moderation-queue" aria-label={t('title')}>
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end sm:justify-between">
        <div>
          <h2 className="text-lg font-semibold">{t('title')}</h2>
          <p className="text-sm text-muted-foreground">{t('description')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Select
            value={statusFilter}
            onValueChange={(value) => setStatusFilter(value as ItemStatus | 'all')}
          >
            <SelectTrigger className="w-[200px]" aria-label={t('statusFilter')}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {QUEUE_FILTERS.map((value) => (
                <SelectItem key={value} value={value}>
                  {t(`filters.${value}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Input
            className="w-[160px]"
            placeholder={t('authorFilter')}
            value={authorVkId}
            onChange={(event) => setAuthorVkId(event.target.value.trim())}
            data-testid="suggest-author-filter"
          />
          {canManage ? (
            <Button
              size="sm"
              variant="outline"
              disabled={syncMutation.isPending}
              onClick={() => syncMutation.mutate()}
              data-testid="suggest-sync-last-100"
            >
              <RefreshCw
                className={`mr-1.5 h-3.5 w-3.5 ${syncMutation.isPending ? 'animate-spin' : ''}`}
                aria-hidden
              />
              {t('syncLast100')}
            </Button>
          ) : null}
        </div>
      </div>

      {canModerate ? (
        <div className="flex flex-wrap items-center gap-2 rounded-md border border-border bg-muted/30 px-3 py-2">
          {selected.length > 0 ? (
            <>
              <span className="text-sm text-muted-foreground">
                {t('selected', { count: selected.length })}
              </span>
              <Button
                size="sm"
                disabled={bulkMutation.isPending}
                onClick={() => bulkMutation.mutate('approve_ids')}
                data-testid="suggest-bulk-approve-selected"
              >
                {t('bulkApproveSelected')}
              </Button>
            </>
          ) : null}
          <Button
            size="sm"
            disabled={bulkMutation.isPending}
            onClick={() => bulkMutation.mutate('approve_all')}
            data-testid="suggest-bulk-approve-all"
          >
            {t('bulkApproveAll')}
          </Button>
          <Button
            size="sm"
            variant="outline"
            disabled={bulkMutation.isPending}
            onClick={() => bulkMutation.mutate('reject_all')}
            data-testid="suggest-bulk-reject-all"
          >
            {t('bulkRejectAll')}
          </Button>
        </div>
      ) : null}

      {job ? (
        <div
          className="rounded-md border border-border px-3 py-2 text-sm"
          data-testid="suggest-bulk-progress"
          role="status"
        >
          {t('bulkProgress', {
            percent: job.progressPercent ?? 0,
            succeeded: job.succeeded ?? 0,
            failed: job.failed ?? 0,
          })}
          <div className="mt-2 h-2 overflow-hidden rounded bg-muted">
            <div
              className="h-full bg-primary transition-all"
              style={{ width: `${Math.min(100, job.progressPercent ?? 0)}%` }}
            />
          </div>
        </div>
      ) : null}

      {itemsQuery.isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
        </div>
      ) : items.length === 0 ? (
        <EmptyState
          icon={<Inbox className="h-6 w-6" aria-hidden />}
          title={t('emptyTitle')}
          description={t('emptyBody')}
        />
      ) : (
        <div>
          {items.map((item) => (
            <ItemCard
              key={item.id}
              item={item}
              selected={selected.includes(item.id!)}
              canModerate={canModerate}
              busy={moderateMutation.isPending}
              onToggle={(id) =>
                setSelected((prev) =>
                  prev.includes(id) ? prev.filter((value) => value !== id) : [...prev, id],
                )
              }
              onApprove={(id) => moderateMutation.mutate({ action: 'approve', itemId: id })}
              onApproveEdit={setEditItem}
              onReject={(id, deleteRemote) =>
                moderateMutation.mutate({ action: 'reject', itemId: id, deleteRemote })
              }
            />
          ))}
        </div>
      )}

      <ApproveEditDialog
        item={editItem}
        open={Boolean(editItem)}
        busy={moderateMutation.isPending}
        warnFallbackNewPost={publishEditMode === 'fallback_new_post'}
        onOpenChange={(open) => {
          if (!open) setEditItem(null);
        }}
        onSubmit={(message) => {
          if (!editItem?.id) return;
          moderateMutation.mutate({
            action: 'edit',
            itemId: editItem.id,
            message,
          });
        }}
      />
    </section>
  );
}
