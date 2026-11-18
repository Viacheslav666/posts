'use client';

import {
  Button,
  ConfirmActionDialog,
  EmptyState,
  Skeleton,
  toast,
} from '@posts/ui';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import { useState } from 'react';

import { getErrorMessage } from '@/shared/lib/errors';

import {
  useAdminQueueMessages,
  useDeleteQueueMessageMutation,
  useIsPlatformAdmin,
  useRetryQueueMessageMutation,
} from '../hooks/use-admin-queries';

type Props = { queue: string };

export function AdminQueueMessagesPage({ queue }: Props) {
  const t = useTranslations('admin.queues');
  const { isPlatformAdmin } = useIsPlatformAdmin();
  const query = useAdminQueueMessages(queue, isPlatformAdmin);
  const retry = useRetryQueueMessageMutation(queue);
  const remove = useDeleteQueueMessageMutation(queue);
  const [pendingRetry, setPendingRetry] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<string | null>(null);

  return (
    <div className="space-y-4" data-testid="admin-queue-messages-page">
      <div>
        <Link href="/admin/queues" className="text-sm text-muted-foreground hover:text-foreground">
          ← {t('back')}
        </Link>
        <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight">
          {t('messagesTitle', { queue })}
        </h1>
        <p className="text-sm text-muted-foreground">{t('messagesDescription')}</p>
      </div>

      {query.isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : query.isError ? (
        <EmptyState title={t('errorTitle')} description={t('errorBody')} />
      ) : !query.data?.items?.length ? (
        <EmptyState title={t('emptyMessagesTitle')} description={t('emptyMessagesBody')} data-testid="admin-queue-empty" />
      ) : (
        <ul className="space-y-3" role="list" data-testid="admin-queue-messages">
          {query.data.items.map((msg) => (
            <li
              key={msg.messageId}
              className="space-y-2 rounded-lg border border-border p-4"
              data-testid={`admin-queue-message-${msg.messageId}`}
            >
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-mono text-xs text-muted-foreground">{msg.messageId}</p>
                  <p className="text-xs text-muted-foreground">
                    {t('redelivered', { count: msg.redeliveredCount ?? 0 })} · {msg.enqueuedAt}
                  </p>
                </div>
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    type="button"
                    onClick={() => setPendingRetry(msg.messageId ?? null)}
                    data-testid={`admin-queue-retry-${msg.messageId}`}
                  >
                    {t('retry')}
                  </Button>
                  <Button
                    size="sm"
                    variant="destructive"
                    type="button"
                    onClick={() => setPendingDelete(msg.messageId ?? null)}
                    data-testid={`admin-queue-delete-${msg.messageId}`}
                  >
                    {t('delete')}
                  </Button>
                </div>
              </div>
              <pre className="overflow-x-auto rounded-md bg-muted/50 p-2 text-xs">{msg.payload}</pre>
            </li>
          ))}
        </ul>
      )}

      <ConfirmActionDialog
        open={Boolean(pendingRetry)}
        onOpenChange={(open) => !open && setPendingRetry(null)}
        title={t('confirmRetryTitle')}
        description={t('confirmRetryBody')}
        confirmLabel={t('retry')}
        cancelLabel={t('cancel')}
        busy={retry.isPending}
        testId="admin-queue-retry-dialog"
        confirmTestId="admin-queue-retry-confirm"
        onConfirm={() => {
          if (!pendingRetry) return;
          retry.mutate(pendingRetry, {
            onSuccess: () => {
              setPendingRetry(null);
              toast({ title: t('retrySuccess') });
            },
            onError: (error) =>
              toast({
                variant: 'destructive',
                title: t('errorTitle'),
                description: getErrorMessage(error),
              }),
          });
        }}
      />

      <ConfirmActionDialog
        open={Boolean(pendingDelete)}
        onOpenChange={(open) => !open && setPendingDelete(null)}
        title={t('confirmDeleteTitle')}
        description={t('confirmDeleteBody')}
        confirmLabel={t('delete')}
        cancelLabel={t('cancel')}
        busy={remove.isPending}
        onConfirm={() => {
          if (!pendingDelete) return;
          remove.mutate(pendingDelete, {
            onSuccess: () => {
              setPendingDelete(null);
              toast({ title: t('deleteSuccess') });
            },
            onError: (error) =>
              toast({
                variant: 'destructive',
                title: t('errorTitle'),
                description: getErrorMessage(error),
              }),
          });
        }}
      />
    </div>
  );
}
