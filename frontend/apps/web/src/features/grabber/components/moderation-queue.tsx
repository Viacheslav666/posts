'use client';

import type { GrabberItem, GrabberRule } from '@posts/api-client';
import {
  Button,
  EmptyState,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Skeleton,
  toast,
} from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Inbox } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import {
  approveIngestItem,
  bulkModerateIngestItems,
  editAndApproveIngestItem,
  listIngestItems,
  rejectIngestItem,
} from '../api/grabber-api';
import type { ItemStatus } from '../model/types';
import { EditApproveDialog } from './edit-approve-dialog';
import { ItemCard } from './item-card';

type ModerationQueueProps = {
  workspaceId: string;
  rules: GrabberRule[];
  canModerate: boolean;
};

const FILTERS: Array<ItemStatus | 'all'> = [
  'pending_moderation',
  'all',
  'rejected',
  'published',
  'approved',
];

export function ModerationQueue({ workspaceId, rules, canModerate }: ModerationQueueProps) {
  const t = useTranslations('grabber.queue');
  const queryClient = useQueryClient();
  const [ruleId, setRuleId] = useState<string>(rules[0]?.id ?? '');
  const [statusFilter, setStatusFilter] = useState<ItemStatus | 'all'>('pending_moderation');
  const [selected, setSelected] = useState<string[]>([]);
  const [editItem, setEditItem] = useState<GrabberItem | null>(null);

  const activeRuleId = ruleId || rules[0]?.id || '';

  const itemsQuery = useQuery({
    queryKey: queryKeys.ingest.items(workspaceId, activeRuleId, {
      status: statusFilter === 'all' ? undefined : statusFilter,
    }),
    queryFn: () =>
      listIngestItems({
        ruleId: activeRuleId,
        status: statusFilter === 'all' ? undefined : statusFilter,
      }),
    enabled: Boolean(activeRuleId),
  });

  const items = useMemo(() => itemsQuery.data?.items ?? [], [itemsQuery.data?.items]);

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: queryKeys.ingest.all(workspaceId) });
  };

  const moderateMutation = useMutation({
    mutationFn: async (input: {
      action: 'approve' | 'reject' | 'edit';
      itemId: string;
      contentText?: string;
    }) => {
      if (input.action === 'approve') return approveIngestItem(input.itemId);
      if (input.action === 'reject') return rejectIngestItem(input.itemId);
      return editAndApproveIngestItem(input.itemId, { contentText: input.contentText ?? '' });
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
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  const bulkMutation = useMutation({
    mutationFn: (action: 'approve' | 'reject') =>
      bulkModerateIngestItems({ action, itemIds: selected }),
    onSuccess: async (result, action) => {
      await invalidate();
      setSelected([]);
      toast({
        title: action === 'approve' ? t('bulkApproveSuccess') : t('bulkRejectSuccess'),
        description: t('bulkResult', {
          succeeded: result.succeeded ?? 0,
          failed: result.failed ?? 0,
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

  if (!rules.length) {
    return (
      <EmptyState
        icon={<Inbox className="h-6 w-6" aria-hidden />}
        title={t('emptyRulesTitle')}
        description={t('emptyRulesBody')}
      />
    );
  }

  return (
    <section className="space-y-4" data-testid="grabber-moderation-queue" aria-label={t('title')}>
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end sm:justify-between">
        <div>
          <h2 className="text-lg font-semibold">{t('title')}</h2>
          <p className="text-sm text-muted-foreground">{t('description')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Select value={activeRuleId} onValueChange={setRuleId}>
            <SelectTrigger className="w-[220px]" aria-label={t('ruleFilter')}>
              <SelectValue placeholder={t('ruleFilter')} />
            </SelectTrigger>
            <SelectContent>
              {rules.map((rule) => (
                <SelectItem key={rule.id} value={rule.id!}>
                  {rule.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={statusFilter}
            onValueChange={(value) => setStatusFilter(value as ItemStatus | 'all')}
          >
            <SelectTrigger className="w-[200px]" aria-label={t('statusFilter')}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {FILTERS.map((value) => (
                <SelectItem key={value} value={value}>
                  {t(`filters.${value}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {canModerate && selected.length > 0 ? (
        <div className="flex flex-wrap items-center gap-2 rounded-md border border-border bg-muted/30 px-3 py-2">
          <span className="text-sm text-muted-foreground">
            {t('selected', { count: selected.length })}
          </span>
          <Button
            size="sm"
            disabled={bulkMutation.isPending}
            onClick={() => bulkMutation.mutate('approve')}
            data-testid="grabber-bulk-approve"
          >
            {t('bulkApprove')}
          </Button>
          <Button
            size="sm"
            variant="outline"
            disabled={bulkMutation.isPending}
            onClick={() => bulkMutation.mutate('reject')}
            data-testid="grabber-bulk-reject"
          >
            {t('bulkReject')}
          </Button>
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
              onReject={(id) => moderateMutation.mutate({ action: 'reject', itemId: id })}
              onEdit={setEditItem}
            />
          ))}
        </div>
      )}

      <EditApproveDialog
        item={editItem}
        open={Boolean(editItem)}
        busy={moderateMutation.isPending}
        onOpenChange={(open) => {
          if (!open) setEditItem(null);
        }}
        onSubmit={(contentText) => {
          if (!editItem?.id) return;
          moderateMutation.mutate({
            action: 'edit',
            itemId: editItem.id,
            contentText,
          });
        }}
      />
    </section>
  );
}
